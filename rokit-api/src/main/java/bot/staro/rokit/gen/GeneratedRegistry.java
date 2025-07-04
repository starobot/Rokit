package bot.staro.rokit.gen;

import bot.staro.rokit.EventConsumer;
import bot.staro.rokit.ListenerRegistry;

import java.util.Map;

/**
 * Implemented once by the class that the annotation‑processor generates.
 * The implementation is discovered through {@link java.util.ServiceLoader}.
 */
public interface GeneratedRegistry {
    void register(ListenerRegistry bus, Object subscriber);

    void unregister(ListenerRegistry bus, Object subscriber);

    int getEventId(Class<?> clazz);

    Class<?>[] eventTypes();

    Map<Object, EventConsumer<?>[]> subscribers();

}