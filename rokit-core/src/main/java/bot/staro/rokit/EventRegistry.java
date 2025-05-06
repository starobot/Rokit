package bot.staro.rokit;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Reflective scanning for now until I figure out something better.
public class EventRegistry implements ListenerRegistry {
    protected final Map<Class<?>, List<EventConsumer<?>>> listeners = new ConcurrentHashMap<>();
    protected final Map<Class<?>, EventWrapper<?>> wrappers = new HashMap<>();

    protected EventRegistry() {
    }

    @SuppressWarnings("unchecked")
    protected <E> void post(E event) {
        List<EventConsumer<?>> list = listeners.get(event.getClass());
        if (list != null) {
            for (EventConsumer<?> c : list) {
                ((EventConsumer<E>) c).accept(event);
            }
        }
    }

    protected void subscribe(Object subscriber) {
        bot.staro.rokit.generated.EventListenerRegistry.register(this, subscriber);
    }

    protected void unsubscribe(Object subscriber) {
        bot.staro.rokit.generated.EventListenerRegistry.unregister(this, subscriber);
    }

    protected boolean isSubscribed(Object subscriber) {
        return bot.staro.rokit.generated.EventListenerRegistry.SUBSCRIBERS.containsKey(subscriber);
    }

    public <T> void internalRegister(Class<T> eventType, EventConsumer<?> c) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(c);
        listeners.get(eventType).sort(Comparator.<EventConsumer<?>>comparingInt(EventConsumer::getPriority)
                .reversed());
    }

    public <T> void internalUnregister(Class<T> eventType, EventConsumer<?> c) {
        List<EventConsumer<?>> list = listeners.get(eventType);
        if (list != null) {
            list.remove(c);
            if (list.isEmpty()) {
                listeners.remove(eventType);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> EventWrapper<T> getWrapper(Class<T> eventType) {
        return (EventWrapper<T>) wrappers.get(eventType);
    }

}
