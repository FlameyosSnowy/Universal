package io.github.flameyossnowy.universal.api.proxy;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.annotations.proxy.*;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@ApiStatus.Internal
@SuppressWarnings({"unchecked", "unused"})
public class ProxiedAdapterHandler<T, ID, C> implements InvocationHandler {
    private final RepositoryAdapter<T, ID, C> adapter;
    private final Class<T> elementType;
    private final Map<String, MethodData> methodCache = new ConcurrentHashMap<>(5);

    public ProxiedAdapterHandler(RepositoryAdapter<T, ID, C> adapter) {
        this.adapter = adapter;
        this.elementType = adapter.getElementType();
    }

    private MethodData getMethodData(@NotNull Method method) {
        return methodCache.computeIfAbsent(method.getName(), name -> {
            Filter[] filters = method.getAnnotationsByType(Filter.class);
            Limit limit = method.getAnnotation(Limit.class);
            OrderBy orderBy = method.getAnnotation(OrderBy.class);
            Insert insert = method.getAnnotation(Insert.class);
            Select select = method.getAnnotation(Select.class);
            Update update = method.getAnnotation(Update.class);

            // Made for performance and safety reasons
            int sum = insert == null ? 0 : 1;
            sum += select == null ? 0 : 1;
            sum += update == null ? 0 : 1;

            if (sum > 1) {
                throw new IllegalStateException("A proxy method cannot have multiple annotations of @Insert, @Select and/or @Update.");
            }

            return new MethodData(method.getName(), filters, limit, orderBy, insert, select, update);
        });
    }

    @Override
    public @Nullable Object invoke(Object proxy, @NotNull Method method, Object[] args) {
        MethodData methodData = this.getMethodData(method);

        switch (method.getName()) {
            case "equals" -> throw new UnsupportedOperationException("Equals is not supported in proxied instances.");
            case "hashCode" -> throw new UnsupportedOperationException("HashCode is not supported in proxied instances.");
            case "toString" -> throw new UnsupportedOperationException("ToString is not supported in proxied instances.");
        }

        if (methodData.insert != null) {
            adapter.insert((T) args[0])
                .ifError(error -> {
                    throw new RuntimeException(error);
                });
            return null;
        } else if (methodData.update != null) {
            adapter.updateAll((T) args[0])
                .ifError(error -> {
                    throw new RuntimeException(error);
                });
            return null;
        } else if (methodData.select != null) {
            var selectQuery = Query.select();
            int size = methodData.filters.length;
            for (int parameterIndex = 0; parameterIndex < size; parameterIndex++) {
                Filter filter = methodData.filters[parameterIndex];
                Object value = args[parameterIndex];

                SelectQuery.SelectQueryBuilder.QueryField where = selectQuery.where(filter.value());
                selectQuery = switch (filter.operator()) {
                    case "=" -> where.eq(value);
                    case ">=" -> where.gte(value);
                    case "<=" -> where.lte(value);
                    case ">" -> where.gt(value);
                    case "<" -> where.lt(value);
                    case "!=" -> where.ne(value);
                    case "IN" -> where.in((Collection<?>) value);
                    case "LIKE" -> where.like((String) value);
                    case "NOT" -> where.not();
                    case "AND" -> where.and();
                    case "OR" -> where.or();
                    default -> throw new IllegalArgumentException("Unsupported operator: " + filter.operator());
                };
            }
            if (methodData.limit != null) selectQuery = selectQuery.limit(methodData.limit.value());
            if (methodData.orderBy != null) selectQuery = selectQuery.orderBy(methodData.orderBy.value(), methodData.orderBy.order());

            Class<?> returnType = method.getReturnType();
            if (returnType == List.class || returnType == Iterable.class) {
                return adapter.find(selectQuery.build());
            } else if (returnType == Set.class) {
                List<T> result = adapter.find(selectQuery.build());
                return result.isEmpty() ? Set.of() : new HashSet<>(result);
            } else if (returnType == elementType) {
                List<T> result = adapter.find(selectQuery.build());
                return result.isEmpty() ? null : result.getFirst();
            } else if (returnType == Iterator.class) {
                List<T> result = adapter.find(selectQuery.build());
                return result.iterator();
            } else if (returnType == Optional.class) {
                List<T> result = adapter.find(selectQuery.build()); // Would use findIterator and findStream, but we can't guarantee that the user will actually use CloseableIterator or manually close the Stream
                return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
            } else if (returnType == Stream.class) {
                List<T> result = adapter.find(selectQuery.build());
                return result.stream();
            } else {
                throw new IllegalStateException("Unsupported return type: " + returnType);
            }
        }

        return null;
    }

    record MethodData(String name, Filter[] filters, Limit limit, OrderBy orderBy, Insert insert, Select select, Update update) { }
}