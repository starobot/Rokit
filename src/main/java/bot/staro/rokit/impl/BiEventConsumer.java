package bot.staro.rokit.impl;

import bot.staro.rokit.EventConsumer;
import bot.staro.rokit.EventWrapper;
import bot.staro.rokit.annotation.TypeHandler;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;

public class BiEventConsumer implements EventConsumer {
    private final Object instance;
    private final Method method;
    private final int priority;
    private final BiConsumer<Object, Object> consumer;
    private final Class<?> type;
    private final EventWrapper eventHandler;

    public BiEventConsumer(Object instance, Method method, int priority) {
        this.instance = instance;
        this.method = method;
        this.priority = priority;
        this.consumer = createConsumer();
        this.type = method.getParameterTypes()[1];
        if (method.getAnnotation(TypeHandler.class) == null) {
            throw new RuntimeException();
        }

        try {
            eventHandler = getWrapperInstance(method);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private BiConsumer<Object, Object> createConsumer() {
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
        Object extra = eventHandler.handle(event);
        if (extra.getClass() != type) {
            return;
        }

        consumer.accept(event, extra);
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

    private static EventWrapper getWrapperInstance(Method method) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        var wrapper = method.getAnnotation(TypeHandler.class).value();
        return (EventWrapper) Objects.requireNonNull(Arrays.stream(wrapper.getConstructors())
                        .filter(constructor -> constructor.getParameterCount() == 0)
                        .findFirst().orElse(null))
                .newInstance();
    }

}
