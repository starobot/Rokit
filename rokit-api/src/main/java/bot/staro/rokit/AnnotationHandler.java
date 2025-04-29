package bot.staro.rokit;

import java.lang.reflect.Method;

@FunctionalInterface
public interface AnnotationHandler {
    <E> EventConsumer<E> createConsumer(ListenerRegistry bus, Object listenerInstance, Method method, int priority, Class<E> eventType);

}
