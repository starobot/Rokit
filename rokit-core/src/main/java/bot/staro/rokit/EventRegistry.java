package bot.staro.rokit;

import bot.staro.rokit.gen.GeneratedRegistry;

import java.util.*;

// We generate an array of listeners from the generated registry and index each listener with its own unique integer id.
// Since the event consumer arrays are already pre-generated and pre-sorted, the overall subscription and dispatching becomes significantly faster.
public class EventRegistry implements ListenerRegistry {
    protected static final GeneratedRegistry REGISTRY;
    protected static final int N;
    protected final List<EventConsumer<?>>[] listenerLists;
    protected final EventConsumer<?>[][] listenerArrays;
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

    @Override
    public <T> void internalRegister(final Class<T> type, final EventConsumer<?> c) {
        final int id = REGISTRY.getEventId(type);
        if (id >= 0) {
            final List<EventConsumer<?>> list = listenerLists[id];
            list.add(c);
            list.sort(Comparator.comparingInt(EventConsumer::getPriority));
            listenerArrays[id] = list.toArray(new EventConsumer<?>[0]);
        }
    }

    @Override
    public <T> void internalUnregister(final Class<T> type, final EventConsumer<?> c) {
        final int id = REGISTRY.getEventId(type);
        if (id >= 0) {
            final List<EventConsumer<?>> list = listenerLists[id];
            if (list.remove(c)) {
                listenerArrays[id] = list.toArray(new EventConsumer<?>[0]);
            }
        }
    }

    @Override
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
