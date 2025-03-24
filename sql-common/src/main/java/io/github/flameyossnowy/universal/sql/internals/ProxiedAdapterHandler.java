package io.github.flameyossnowy.universal.sql.internals;

import io.github.flameyossnowy.universal.api.annotations.proxy.*;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.options.UpdateQuery;
import io.github.flameyossnowy.universal.sql.RelationalRepositoryAdapter;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProxiedAdapterHandler<T, ID> implements InvocationHandler {
    private final RelationalRepositoryAdapter<T, ID> adapter;
    private final Map<String, MethodData> methodCache = new ConcurrentHashMap<>();

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
            int sum = 0;
            sum += filters.length;

            if (limit != null) sum += 1;
            if (orderBy != null) sum += 1;
            // Made for performance and safety reasons

            if (insert != null && select != null) throw new IllegalArgumentException("Cannot have both insert and select");
            if (update != null && select != null) throw new IllegalArgumentException("Cannot have both update and select");
            if (insert != null && update != null) throw new IllegalArgumentException("Cannot have both insert and update");

            return new MethodData(method.getName(), filters, limit, orderBy, insert, select, update, sum);
        });
    }

    @Override
    public Object invoke(Object proxy, @NotNull Method method, Object[] args) throws Throwable {
        MethodData methodData = this.getMethodData(method);

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
            var result = adapter.find(selectQuery.build());
            return result.list();
        }

        return null;
    }

    record MethodData(String name, Filter[] filters, Limit limit, OrderBy orderBy, Insert insert, Select select, Update update, int sum) { }
}
