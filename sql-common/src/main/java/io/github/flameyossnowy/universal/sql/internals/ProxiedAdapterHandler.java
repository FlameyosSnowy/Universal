package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.annotations.proxy.*;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import io.github.flameyossnowy.universal.sql.RelationalRepositoryAdapter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public class ProxiedAdapterHandler<T, ID> implements InvocationHandler {
    private final RelationalRepositoryAdapter<T, ID> adapter;
    private final Map<String, MethodData> methodCache = new ConcurrentHashMap<>(5);

    ProxiedAdapterHandler(RelationalRepositoryAdapter<T, ID> adapter) {
        this.adapter = adapter;
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
            int sum = 0, annotationSum = 0;
            sum += filters.length;

            annotationSum += insert == null ? 0 : 1;
            annotationSum += select == null ? 0 : 1;
            annotationSum += update == null ? 0 : 1;

            if (annotationSum > 1) {
                throw new IllegalStateException("A proxy method cannot have multiple annotations of @Insert, @Select and/or @Update.");
            }

            if (limit != null) sum += 1;
            if (orderBy != null) sum += 1;
            return new MethodData(method.getName(), filters, limit, orderBy, insert, select, update, sum);
        });
    }

    @Override
    public Object invoke(Object proxy, @NotNull Method method, Object[] args) throws Throwable {
        MethodData methodData = this.getMethodData(method);

        switch (method.getName()) {
            case "equals" -> throw new UnsupportedOperationException("Equals is not supported in proxied instances.");
            case "hashCode" -> throw new UnsupportedOperationException("HashCode is not supported in proxied instances.");
            case "toString" -> throw new UnsupportedOperationException("ToString is not supported in proxied instances.");
        }

        if (methodData.sum == 0) return null;

        if (methodData.insert != null) {
            adapter.insert((T) args[0]);
            return null;
        } else if (methodData.update != null) {
            adapter.updateAll((UpdateQuery) args[0]);
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
            return adapter.find(selectQuery.build());
        }

        return null;
    }

    record MethodData(String name, Filter[] filters, Limit limit, OrderBy orderBy, Insert insert, Select select, Update update, int sum) { }
}
