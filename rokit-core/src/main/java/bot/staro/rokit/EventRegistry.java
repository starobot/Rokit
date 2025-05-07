package bot.staro.rokit;

import bot.staro.rokit.gen.GeneratedRegistry;

import java.util.*;

// We generate an array of listeners from the generated registry and index each listener with its own unique integer id.
// Since the event consumer arrays are already pre-generated and pre-sorted, the overall subscription and dispatching becomes significantly faster.
public class EventRegistry implements ListenerRegistry {
    private static final GeneratedRegistry REGISTRY;
    private static final int N;
    private final List<EventConsumer<?>>[] listenerLists;
    private final EventConsumer<?>[][] listenerArrays;
    protected final Map<Class<?>, EventWrapper<?>> wrappers;

    @SuppressWarnings("unchecked")
    protected EventRegistry() {
        listenerLists = (List<EventConsumer<?>>[]) new List[N];
        listenerArrays = new EventConsumer<?>[N][];
        wrappers  = new IdentityHashMap<>();
        for (int i = 0; i < N; i++) {
            listenerLists[i] = new ArrayList<>();
            listenerArrays[i] = new EventConsumer<?>[0];
        }
    }

    @SuppressWarnings({"unchecked"})
    protected <E> void post(final E event) {
        final int id = REGISTRY.getEventId(event.getClass());
        if (id >= 0) {
            final EventConsumer<E>[] arr = (EventConsumer<E>[]) listenerArrays[id];
            int i = arr.length;
            while (i-- != 0) {
                arr[i].accept(event);
            }
        }
    }

    protected void subscribe(final Object subscriber) {
        REGISTRY.register(this, subscriber);
    }

    protected void unsubscribe(final Object subscriber) {
        REGISTRY.unregister(this, subscriber);
    }

    protected boolean isSubscribed(final Object subscriber) {
        return REGISTRY.subscribers().containsKey(subscriber);
    }

    public <T> void internalRegister(final Class<T> type, final EventConsumer<?> c) {
        final int id = REGISTRY.getEventId(type);
        if (id >= 0) {
            final List<EventConsumer<?>> list = listenerLists[id];
            list.add(c);
            list.sort(Comparator.<EventConsumer<?>>comparingInt(EventConsumer::getPriority).reversed());
            listenerArrays[id] = list.toArray(new EventConsumer<?>[0]);
        }
    }

    public <T> void internalUnregister(final Class<T> type, final EventConsumer<?> c) {
        final int id = REGISTRY.getEventId(type);
        if (id >= 0) {
            final List<EventConsumer<?>> list = listenerLists[id];
            if (list.remove(c)) {
                listenerArrays[id] = list.toArray(new EventConsumer<?>[0]);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> EventWrapper<T> getWrapper(final Class<T> eventType) {
        return (EventWrapper<T>) wrappers.get(eventType);
    }

    static {
        REGISTRY = ServiceLoader.load(GeneratedRegistry.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No Rokit listener registry found on class‑path"));
        N = REGISTRY.eventTypes().length;
    }

}
