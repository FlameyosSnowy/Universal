package io.github.flameyossnowy.universal.api.utils;

import java.util.Map;

public final class Primitives {
    private Primitives() {}

    private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER = Map.of(
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class,
            short.class, Short.class,
            byte.class, Byte.class,
            boolean.class, Boolean.class,
            char.class, Character.class
    );

    private static final Map<Class<?>, Class<?>> WRAPPER_TO_PRIMITIVE = Map.of(
            Byte.class, byte.class,
            Short.class, short.class,
            Integer.class, int.class,
            Long.class, long.class,
            Boolean.class, boolean.class,
            Character.class, char.class,
            Float.class, float.class,
            Double.class, double.class
    );

    public static Class<?> asWrapper(Class<?> type) {
        return PRIMITIVE_TO_WRAPPER.getOrDefault(type, type);
    }

    public static Class<?> asPrimitive(Class<?> type) {
        return WRAPPER_TO_PRIMITIVE.getOrDefault(type, type);
    }
}
