package bot.staro.rokit.impl;

import bot.staro.rokit.EventConsumer;
import bot.staro.rokit.function.EventWrapper;
import bot.staro.rokit.utils.ReflectionUtil;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;

public class MultiEventConsumer implements EventConsumer {
    private final Object instance;
    private final Method method;
    private final int priority;
    private final BiConsumer<Object, Object> consumer;
    private final Class<?>[] types;
    private final EventWrapper<?> eventWrapper;

    public MultiEventConsumer(Object instance, Method method, int priority, EventWrapper<?> eventHandler) {
        this.instance = instance;
        this.method = method;
        this.priority = priority;
        this.consumer = createConsumer();
        this.types = Arrays.copyOfRange(method.getParameterTypes(), 1, method.getParameterTypes().length );
        this.eventWrapper = eventHandler;
    }

    @SuppressWarnings("unchecked")
    private BiConsumer<Object, Object> createConsumer() {
        if (method.getParameters().length > 2) return null; // hack
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(this.method.getDeclaringClass(), MethodHandles.lookup());
            MethodType methodType = MethodType.methodType(void.class, method.getParameters()[0].getType(), method.getParameters()[1].getType());
            MethodHandle methodHandle = lookup.findVirtual(method.getDeclaringClass(), method.getName(), methodType);
            MethodType invokedType = MethodType.methodType(BiConsumer.class, method.getDeclaringClass());
            MethodHandle lambdaFactory = LambdaMetafactory.metafactory(lookup,
                            "accept",
                            invokedType,
                            MethodType.methodType(void.class, Object.class, Object.class),
                            methodHandle, methodType)
                    .getTarget();
            return (BiConsumer<Object, Object>) lambdaFactory.invoke(instance);
        } catch (Throwable throwable) {
            throw new IllegalStateException(throwable.getMessage());
        }
    }

    @Override
    public void invoke(Object event) {
        Object[] extra = eventWrapper.invoke(event);
        if (extra.length != types.length) {
            return;
        }

        for (int i = 0; i < extra.length; i++) {
            if (!ReflectionUtil.toNonPrimitive(types[i]).isInstance(extra[i])) {
                return;
            }
        }

        if (extra.length == 1) {
            consumer.accept(event, extra[0]);
        } else {
            try {
                method.invoke(instance, add(extra, event));
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static Object[] add(Object[] a, Object o) {
        ArrayList<Object> r = new ArrayList<>(List.of(a));
        r.addFirst(o);
        return r.toArray();
    }

    @Override
    public Object getInstance() {
        return instance;
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        EventConsumer that = (EventConsumer) obj;
        return getInstance().equals(that.getInstance()) && getMethod().equals(that.getMethod());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getInstance(), getMethod());
    }

}
