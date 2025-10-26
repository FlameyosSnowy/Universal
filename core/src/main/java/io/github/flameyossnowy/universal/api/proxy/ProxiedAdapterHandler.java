package io.github.flameyossnowy.universal.api.proxy;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.annotations.proxy.*;
import io.github.flameyossnowy.universal.api.options.Query;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@ApiStatus.Internal
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
    public Object invoke(Object proxy, @NotNull Method method, Object[] args) {
        MethodData methodData = this.getMethodData(method);

        switch (method.getName()) {
            case "equals" -> throw new UnsupportedOperationException("Equals is not supported in proxied instances.");
            case "hashCode" -> throw new UnsupportedOperationException("HashCode is not supported in proxied instances.");
            case "toString" -> throw new UnsupportedOperationException("ToString is not supported in proxied instances.");
        }

        if (methodData.insert != null) {
            adapter.insert((T) args[0]);
            return null;
        } else if (methodData.update != null) {
            adapter.updateAll((T) args[0]);
            return null;
        } else if (methodData.select != null) {
            var selectQuery = Query.select();
            int size = methodData.filters.length;
            for (int parameterIndex = 0; parameterIndex < size; parameterIndex++) {
                Filter filter = methodData.filters[parameterIndex];
                Object value = args[parameterIndex];
                selectQuery = selectQuery.where(filter.value(), filter.operator(), value);
            }
            if (methodData.limit != null) selectQuery = selectQuery.limit(methodData.limit.value());
            if (methodData.orderBy != null) selectQuery = selectQuery.orderBy(methodData.orderBy.value(), methodData.orderBy.order());
            List<T> result = adapter.find(selectQuery.build());

            Class<?> returnType = method.getReturnType();
            if (returnType == List.class || returnType == Iterable.class) {
                return result;
            } else if (returnType == Set.class) {
                return result.isEmpty() ? Set.of() : new HashSet<>(result);
            } else if (returnType == elementType) {
                return result.isEmpty() ? null : result.get(0);
            } else if (returnType == Iterator.class) {
                return result.iterator();
            } else if (returnType == Optional.class) {
                return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
            } else if (returnType == Stream.class) {
                return result.stream();
            } else {
                throw new IllegalStateException("Unsupported return type: " + returnType);
            }
        }

        return null;
    }

    record MethodData(String name, Filter[] filters, Limit limit, OrderBy orderBy, Insert insert, Select select, Update update) { }
}