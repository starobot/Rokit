package bot.staro.rokit;

import java.lang.reflect.Method;

/**
 * A listener object.
 */
public interface EventListener {
    void invoke(Object event);

    Object getInstance();

    Method getMethod();

    int getPriority();

}
