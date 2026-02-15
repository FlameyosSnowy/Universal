package io.github.flameyossnowy.universal.sql.params;

import io.github.flameyossnowy.universal.api.handler.CollectionHandler;
import io.github.flameyossnowy.universal.api.handler.DataHandler;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Primitives;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("unchecked")
public class SQLDatabaseParameters implements DatabaseParameters {

    private final PreparedStatement statement;
    private final TypeResolverRegistry typeRegistry;
    private final CollectionHandler collectionHandler;

    private int parameterIndex = 1;
    private final boolean supportsArrays;
    private final Map<String, Integer> nameToIndexMap = new LinkedHashMap<>(8);

    private final Map<String, Map<String, Integer>> whereCache = new HashMap<>(8);

    @Contract("null, _, _, _, _, _ -> fail; !null, null, _, _, _, _ -> fail")
    public SQLDatabaseParameters(
        PreparedStatement statement,
        TypeResolverRegistry typeRegistry,
        String sql,
        RepositoryModel<?, ?> information,
        CollectionHandler collectionHandler,
        boolean supportsArrays
    ) {
        if (statement == null) throw new IllegalArgumentException("PreparedStatement cannot be null");
        if (typeRegistry == null) throw new IllegalArgumentException("TypeResolverRegistry cannot be null");

        this.statement = statement;
        this.typeRegistry = typeRegistry;
        this.collectionHandler = collectionHandler;
        this.supportsArrays = supportsArrays;

        parseSql(sql, information);
    }

    private void parseSql(@NotNull String sql, RepositoryModel<?, ?> information) {
        String lower = sql.toLowerCase(Locale.ROOT).trim();

        if (lower.startsWith("insert")) parseInsert(sql, information);
        else if (lower.startsWith("update")) parseUpdate(sql);
        else if (lower.startsWith("select")) parseWhereClause(sql);
        else if (lower.startsWith("delete")) parseWhereClause(sql);
    }

    private void parseWhereClause(String sql) {
        int wherePos = sql.toLowerCase(Locale.ROOT).indexOf("where");
        if (wherePos < 0) return;

        String where = sql.substring(wherePos + 5);
        parseWhere(where);
    }

    private void parseWhere(String where) {
        Map<String, Integer> cached = whereCache.get(where);
        if (cached != null) {
            nameToIndexMap.putAll(cached);
            return;
        }

        Map<String, Integer> mapping = new LinkedHashMap<>(32);
        List<Token> tokens = SqlTokenizer.tokenize(where);

        int pos = 1;

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);

            if (t.type() != TokenType.IDENT) continue;

            // IDENT OP ?
            if (i + 2 < tokens.size()) {
                Token op = tokens.get(i + 1);
                Token rhs = tokens.get(i + 2);

                if (op.type() == TokenType.OPERATOR && rhs.type() == TokenType.QUESTION) {
                    mapping.put(t.text(), pos++);
                    continue;
                }

                // IDENT IN (...)
                if (op.type() == TokenType.KEYWORD && op.text().equalsIgnoreCase("in")) {
                    int j = i + 2;
                    if (j < tokens.size() && tokens.get(j).type() == TokenType.LPAREN) {
                        j++;
                        while (j < tokens.size() && tokens.get(j).type() != TokenType.RPAREN) {
                            if (tokens.get(j).type() == TokenType.QUESTION) {
                                mapping.put(t.text(), pos++);
                            }
                            j++;
                        }
                    }
                }
            }
        }

        whereCache.put(where, mapping);
        nameToIndexMap.putAll(mapping);
    }

    // ───────── existing insert/update parsing unchanged ─────────

    private void parseInsert(String sql, RepositoryModel<?, ?> information) {
        int openParen = sql.indexOf('(');
        int closeParen = sql.indexOf(')', openParen);
        if (openParen < 0 || closeParen < 0) return;

        String[] columns = sql.substring(openParen + 1, closeParen).split(",");

        FieldModel<?> autoIncrement = information.getPrimaryKey().autoIncrement()
            ? information.getPrimaryKey()
            : null;

        int pos = 1;
        for (String raw : columns) {
            String col = raw.trim();
            if (autoIncrement != null && col.equalsIgnoreCase(autoIncrement.name())) continue;
            nameToIndexMap.put(col, pos++);
        }

        parameterIndex = pos;
    }

    private void parseUpdate(String sql) {
        int setIndex = sql.toLowerCase(Locale.ROOT).indexOf("set");
        if (setIndex < 0) return;

        String[] assigns = sql.substring(setIndex + 3).split(",");
        int pos = 1;

        for (String a : assigns) {
            int eq = a.indexOf('=');
            if (eq < 0) continue;
            String col = a.substring(0, eq).trim();
            if (!col.isEmpty()) nameToIndexMap.put(col, pos++);
        }
    }

    private int getIndexForName(Object index) {
        if (index instanceof Integer i) return i;
        if (index instanceof String s) {
            // Allow positional binding for parameterized SQL (e.g. aggregation/HAVING) by
            // passing "1", "2", ... as the parameter name.
            int len = s.length();
            if (len > 0) {
                int n = 0;
                for (int i1 = 0; i1 < len; i1++) {
                    char c = s.charAt(i1);
                    if (c < '0' || c > '9') {
                        n = -1;
                        break;
                    }
                    n = n * 10 + (c - '0');
                }
                if (n > 0) {
                    return n;
                }
            }
        }
        Integer mapped = nameToIndexMap.get(index.toString());
        if (mapped == null) throw new IllegalArgumentException("Unknown parameter: " + index);
        return mapped;
    }

    @Override
    public CollectionHandler getCollectionHandler() {
        return collectionHandler;
    }

    @Override
    public String getAdapterType() {
        return "sql";
    }

    @Override
    public boolean supportsArraysNatively() {
        return supportsArrays;
    }

    @Override
    public <T> void set(@NotNull String name, @Nullable T value, @NotNull Class<?> type) {
        int idx = getIndexForName(name);

        if (value == null) {
            try { statement.setObject(idx, null); }
            catch (SQLException e) { throw new RuntimeException(e); }
            return;
        }

        TypeResolver<Object> resolver = (TypeResolver<Object>) typeRegistry.resolve(Primitives.asWrapper(type));
        if (resolver != null) {
            resolver.insert(this, name, value);
            return;
        }

        setRaw(name, value, type);
    }

    @Override
    public <T> void setRaw(@NotNull String name, @Nullable T value, @NotNull Class<?> type) {
        int idx = getIndexForName(name);

        if (value == null) {
            setNull(name, type);
            return;
        }

        try {
            if (type == byte.class || type == Byte.class)             statement.setByte(idx, ((Number)value).byteValue());
            else if (type == short.class || type == Short.class)      statement.setShort(idx, ((Number)value).shortValue());
            else if (type == int.class || type == Integer.class)      statement.setInt(idx, ((Number)value).intValue());
            else if (type == long.class || type == Long.class)        statement.setLong(idx, ((Number)value).longValue());
            else if (type == float.class || type == Float.class)      statement.setFloat(idx, ((Number)value).floatValue());
            else if (type == double.class || type == Double.class)    statement.setDouble(idx, ((Number)value).doubleValue());
            else if (type == boolean.class || type == Boolean.class)  statement.setBoolean(idx, (Boolean)value);
            else if (type == char.class || type == Character.class)   statement.setString(idx, value.toString());
            else if (type == String.class)                            statement.setString(idx, (String) value);
            else                                                      statement.setObject(idx, value); // I hope this never gets touched, lol

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void setNull(int index, @NotNull Class<?> type) {
        Class<?> lookup = Primitives.asWrapper(type);

        DataHandler<?> handler = typeRegistry.getHandler(lookup);
        int sqlType = handler != null ? handler.getSqlType() : Types.OTHER;

        try { statement.setNull(index, sqlType); }
        catch (SQLException e) { throw new RuntimeException(e); }
    }

    @Override
    public void setNull(@NotNull String name, @NotNull Class<?> type) {
        int index = nameToIndexMap.computeIfAbsent(name, n -> parameterIndex++);
        setNull(index, type);
    }

    // ────────────────────────────────────────────────────────────────────────────────

    @Override public int size() { return Math.max(parameterIndex - 1, nameToIndexMap.size()); }
    @Override public <T> @Nullable T get(int idx, @NotNull Class<T> type) { throw new UnsupportedOperationException(); }
    @Override public <T> @Nullable T get(@NotNull String name, @NotNull Class<T> type) { throw new UnsupportedOperationException(); }
    @Override public boolean contains(@NotNull String name) { return nameToIndexMap.containsKey(name); }
    public PreparedStatement getStatement() { return statement; }
}
