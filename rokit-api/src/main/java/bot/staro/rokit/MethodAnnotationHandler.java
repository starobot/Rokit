package bot.staro.rokit;

import java.lang.reflect.Method;

public interface MethodAnnotationHandler extends AnnotationHandler {
    <E> EventConsumer<E> createConsumer(ListenerRegistry bus, Object listenerInstance, Method method, int priority, Class<E> eventType);

}
