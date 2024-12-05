package bot.staro.rokit.utils;

import java.util.Map;

public final class ReflectionUtil {
    public static final Map<Class<?>, Class<?>> PRIMITIVES = Map.of(
            int.class, Integer.class,
            byte.class, Byte.class,
            short.class, Short.class,
            long.class, Long.class,
            boolean.class, Boolean.class,
            float.class, Float.class,
            double.class, Double.class,
            char.class, Character.class
    );

    private ReflectionUtil() {
        throw new AssertionError();
    }

    public static Class<?> toNonPrimitive(Class<?> klass) {
        if (!klass.isPrimitive()) return klass;
        return PRIMITIVES.get(klass);
    }

}
