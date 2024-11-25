package bot.staro.rokit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.function.BiFunction;

public interface EventBus {
    void post(Object event);

    void subscribe(Object instance);

    void unsubscribe(Object instance);

    boolean isSubscribed(Object instance);

    void registerListenerFactory(Class<? extends Annotation> annotationType, BiFunction<Object, Method, EventListener> factory);

}

