package io.github.flameyossnowy.universal.sql.params;

import io.github.flameyossnowy.universal.api.handler.DataHandler;
import io.github.flameyossnowy.universal.api.handler.PrimitiveHandler;
import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.resolver.TypeResolver;
import io.github.flameyossnowy.universal.api.resolver.TypeResolverRegistry;
import io.github.flameyossnowy.universal.api.utils.Primitives;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL implementation of DatabaseParameters using a JDBC PreparedStatement.
 * Supports named and positional parameters, INSERT column lists,
 * UPDATE assignments, and avoids regex for high performance.
 */
@SuppressWarnings("unchecked")
public class SQLDatabaseParameters implements DatabaseParameters {
    private final PreparedStatement statement;
    private final TypeResolverRegistry typeRegistry;

    private int parameterIndex = 1;
    private final Map<String, Integer> nameToIndexMap = new LinkedHashMap<>(4);

    public SQLDatabaseParameters(PreparedStatement statement, TypeResolverRegistry typeRegistry, String sql, RepositoryInformation information) {
        if (statement == null) throw new IllegalArgumentException("PreparedStatement cannot be null");
        if (typeRegistry == null) throw new IllegalArgumentException("TypeResolverRegistry cannot be null");

        this.statement = statement;
        this.typeRegistry = typeRegistry;

        parseSql(sql, information);
    }

    private void parseSql(String sql, RepositoryInformation information) {
        String lower = sql.toLowerCase().trim();

        if (lower.startsWith("insert")) {
            parseInsert(sql, information);
        } else if (lower.startsWith("update")) {
            parseUpdate(sql);
        } else if (lower.startsWith("select")) {
            parseSelect(sql);
        } else if (lower.startsWith("delete")) {
            parseDelete(sql);
        }
    }

    private void parseSelect(String sql) {
        int wherePos = sql.toLowerCase().indexOf("where");
        if (wherePos < 0) return;

        String whereClause = sql.substring(wherePos + 5);
        parseWhere(whereClause);
    }

    private void parseDelete(String sql) {
        int wherePos = sql.toLowerCase().indexOf("where");
        if (wherePos < 0) return;

        String whereClause = sql.substring(wherePos + 5);
        parseWhere(whereClause);
    }


    private final Map<String, List<FieldIndex>> whereCache = new HashMap<>(6);

    private static class FieldIndex {
        final int start;
        final int end;
        final int index;

        FieldIndex(int start, int end, int index) {
            this.start = start;
            this.end = end;
            this.index = index;
        }
    }

    private void parseWhere(String where) {
        List<FieldIndex> cached = whereCache.get(where);
        if (cached != null) {
            for (FieldIndex fi : cached) {
                nameToIndexMap.put(where.substring(fi.start, fi.end).trim(), fi.index);
            }
            return;
        }

        List<FieldIndex> resultList = new ArrayList<>();
        String w = where;
        int len = w.length();
        int pos = 1;
        int start = 0;
        int opPos = -1;

        for (int i = 0; i < len; i++) {
            char c = Character.toLowerCase(w.charAt(i));

            // detect operator
            if (opPos < 0) {
                if (c == '=' || c == '<' || c == '>') {
                    opPos = i;
                } else if (c == 'l' && i + 3 < len &&
                    Character.toLowerCase(w.charAt(i + 1)) == 'i' &&
                    Character.toLowerCase(w.charAt(i + 2)) == 'k' &&
                    Character.toLowerCase(w.charAt(i + 3)) == 'e') {
                    opPos = i;
                }
            }

            // detect separators
            if (w.regionMatches(true, i, " and ", 0, 5) || w.regionMatches(true, i, " or ", 0, 4)) {
                if (opPos >= 0) {
                    resultList.add(new FieldIndex(start, opPos, pos++));
                }
                start = i + (w.regionMatches(true, i, " and ", 0, 5) ? 5 : 4);
                i = start - 1;
                opPos = -1;
            }
        }

        // last condition
        if (opPos >= 0 && start < len) {
            resultList.add(new FieldIndex(start, opPos, pos++));
        }

        whereCache.put(where, resultList);

        // populate nameToIndexMap
        for (FieldIndex fi : resultList) {
            nameToIndexMap.put(where.substring(fi.start, fi.end).trim(), fi.index);
        }
    }

    private void parseInsert(String sql, RepositoryInformation information) {
        int openParen = sql.indexOf('(');
        int closeParen = sql.indexOf(')', openParen);
        if (openParen < 0 || closeParen < 0) return;

        String[] columns = sql.substring(openParen + 1, closeParen).split(",");

        // find auto-increment field once
        FieldData<?> autoIncrementField = null;
        for (FieldData<?> f : information.getFields()) {
            if (f.autoIncrement()) {
                autoIncrementField = f;
                break;
            }
        }

        int pos = 1;
        for (String rawCol : columns) {
            String col = rawCol.trim();
            if (autoIncrementField != null && col.equalsIgnoreCase(autoIncrementField.name())) {
                continue; // skip auto-increment column
            }
            nameToIndexMap.put(col, pos++);
        }

        parameterIndex = pos;
    }

    private void parseUpdate(String sql) {
        int setIndex = sql.toLowerCase().indexOf("set");
        if (setIndex < 0) return;

        String afterSet = sql.substring(setIndex + 3);

        String[] assignments = afterSet.split(",");
        int pos = 1;

        for (String assign : assignments) {
            int eq = assign.indexOf('=');
            if (eq < 0) continue;

            String col = assign.substring(0, eq).trim();
            if (col.isEmpty()) continue;

            nameToIndexMap.put(col, pos++);
        }
    }

    private int getIndexForName(Object index) {
        if (index instanceof Integer i)
            return i;

        String name = index.toString();
        Integer mapped = nameToIndexMap.get(name);

        if (mapped == null)
            throw new IllegalArgumentException("Unknown parameter name: " + name);

        return mapped;
    }

    // ────────────────────────────────────────────────────────────────────────────────
    //   PARAM SETTING
    // ────────────────────────────────────────────────────────────────────────────────

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
