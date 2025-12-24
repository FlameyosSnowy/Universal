package io.github.flameyossnowy.universal.api.handler;

import io.github.flameyossnowy.universal.api.RepositoryAdapter;
import io.github.flameyossnowy.universal.api.options.Query;
import io.github.flameyossnowy.universal.api.options.SelectQuery;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.OneToOneField;
import io.github.flameyossnowy.universal.api.utils.Logging;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.matcher.ElementMatchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

public class OneToOneLazyProxy {

    /**
     * Creates a lazy proxy or returns the value directly based on the lazy flag.
     */
    @Nullable
    public static <ID> Object createOrFetch(
            @NotNull FieldData<?> field,
            ID primaryKeyValue,
            @NotNull OneToOneField link,
            @NotNull RepositoryAdapter<Object, Object, ?> adapter,
            String cacheKey,
            Runnable cacheSetter
    ) {
        if (field.oneToOne().lazy()) {
            // Create lazy proxy
            return createLazyProxy(
                    field.type(),
                    () -> fetchOneToOneResult(primaryKeyValue, field, link, adapter, cacheKey, cacheSetter)
            );
        } else {
            // Eagerly fetch
            return fetchOneToOneResult(primaryKeyValue, field, link, adapter, cacheKey, cacheSetter);
        }
    }

    /**
     * Creates a ByteBuddy proxy that lazily loads the target entity.
     */
    @SuppressWarnings("unchecked")
    private static <T> T createLazyProxy(Class<T> targetType, Supplier<Object> loader) {
        try {
            LazyLoadInterceptor interceptor = new LazyLoadInterceptor(loader);
            
            return new ByteBuddy()
                    .subclass(targetType)
                    .method(ElementMatchers.any())
                    .intercept(MethodDelegation.to(interceptor))
                    .make()
                    .load(targetType.getClassLoader())
                    .getLoaded()
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (Exception e) {
            Logging.error("Failed to create lazy proxy for type: " + targetType.getName(), e);
            // Fallback to eager loading
            return (T) loader.get();
        }
    }

    /**
     * Fetches the OneToOne result from the database.
     */
    @Nullable
    private static <ID> Object fetchOneToOneResult(
            ID primaryKeyValue,
            @NotNull FieldData<?> field,
            @NotNull OneToOneField link,
            @NotNull RepositoryAdapter<Object, Object, ?> adapter,
            String cacheKey,
            Runnable cacheSetter
    ) {
        try {
            SelectQuery query = Query.select()
                    .where(link.name(), primaryKeyValue)
                    .limit(1)
                    .build();

            List<Object> results = adapter.find(query);
            Object result = (results == null || results.isEmpty()) ? null : results.getFirst();

            // Update cache
            if (cacheSetter != null) {
                cacheSetter.run();
            }

            return result;
        } catch (Exception e) {
            Logging.error("Error loading OneToOne relationship for field: " + field.name(), e);
            return null;
        }
    }

    /**
     * Interceptor for lazy loading. Loads the target entity on first method invocation.
     */
    public static class LazyLoadInterceptor {
        private final Supplier<Object> loader;
        private volatile Object target;
        private volatile boolean loaded = false;

        public LazyLoadInterceptor(Supplier<Object> loader) {
            this.loader = loader;
        }

        @RuntimeType
        public Object intercept(@Origin Method method) throws Throwable {
            // Double-checked locking for thread safety
            if (!loaded) {
                synchronized (this) {
                    if (!loaded) {
                        target = loader.get();
                        loaded = true;
                    }
                }
            }

            // If target is null, return appropriate default
            if (target == null) {
                Class<?> returnType = method.getReturnType();
                if (returnType.isPrimitive()) {
                    if (returnType == boolean.class) return false;
                    if (returnType == int.class) return 0;
                    if (returnType == long.class) return 0L;
                    if (returnType == double.class) return 0.0;
                    if (returnType == float.class) return 0.0f;
                    if (returnType == byte.class) return (byte) 0;
                    if (returnType == short.class) return (short) 0;
                    if (returnType == char.class) return '\0';
                }
                return null;
            }

            // Invoke the method on the loaded target
            return method.invoke(target);
        }
    }
}