package bot.staro.rokit;

import java.util.*;

// This is cursed, but it's worth it.
// We generate an array of listeners from the generated registry and index each listener with its own unique integer id.
// Since the event consumer arrays are already pre-generated and pre-sorted, the overall subscription and dispatching becomes significantly faster.
public class EventRegistry implements ListenerRegistry {
    private static final int N = bot.staro.rokit.generated.EventListenerRegistry.EVENT_TYPES.length;
    @SuppressWarnings("unchecked")
    private final List<EventConsumer<?>>[] listenerLists = (List<EventConsumer<?>>[]) new List[N];
    private final EventConsumer<?>[][] listenerArrays = new EventConsumer<?>[N][];
    protected final Map<Class<?>, EventWrapper<?>> wrappers = new HashMap<>();

    protected EventRegistry() {
        for (int i = 0; i < N; i++) {
            listenerLists[i]  = new ArrayList<>();
            listenerArrays[i] = new EventConsumer<?>[0];
        }
    }

    @SuppressWarnings({"unchecked", "ForLoopReplaceableByForEach"})
    protected <E> void post(E event) {
        int id = bot.staro.rokit.generated.EventListenerRegistry.getEventId(event.getClass());
        if (id >= 0) {
            EventConsumer<E>[] arr = (EventConsumer<E>[]) listenerArrays[id];
            for (int i = 0, len = arr.length; i < len; i++) {
                arr[i].accept(event);
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
        int id = bot.staro.rokit.generated.EventListenerRegistry.getEventId(eventType);
        if (id >= 0) {
            List<EventConsumer<?>> list = listenerLists[id];
            list.add(c);
            list.sort(Comparator.<EventConsumer<?>>comparingInt(EventConsumer::getPriority).reversed());
            listenerArrays[id] = list.toArray(new EventConsumer<?>[0]);
        }
    }

    public <T> void internalUnregister(Class<T> eventType, EventConsumer<?> c) {
        int id = bot.staro.rokit.generated.EventListenerRegistry.getEventId(eventType);
        if (id >= 0) {
            List<EventConsumer<?>> list = listenerLists[id];
            if (list.remove(c)) {
                listenerArrays[id] = list.toArray(new EventConsumer<?>[0]);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> EventWrapper<T> getWrapper(Class<T> eventType) {
        return (EventWrapper<T>) wrappers.get(eventType);
    }

}
