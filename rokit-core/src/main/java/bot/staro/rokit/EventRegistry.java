package bot.staro.rokit;

import bot.staro.rokit.gen.GeneratedRegistry;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// Moonrise...
public class EventRegistry implements ListenerRegistry {
    protected static final GeneratedRegistry REGISTRY = ServiceLoader.load(GeneratedRegistry.class)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No Rokit listener registry found on class‑path"));
    protected final Map<Class<?>, EventWrapper<?>> wrappers = new IdentityHashMap<>();
    protected volatile EventConsumer<?>[][] listeners = new EventConsumer<?>[REGISTRY.eventTypes().length][0];
    private final ReentrantReadWriteLock[] locks = Arrays.stream(REGISTRY.eventTypes())
            .map(t -> new ReentrantReadWriteLock())
            .toArray(ReentrantReadWriteLock[]::new);

    @Override
    public <T> void internalRegister(final Class<T> eventType, final EventConsumer<?> consumer) {
        final int id = REGISTRY.getEventId(eventType);
        if (id > -1) {
            final ReentrantReadWriteLock.WriteLock w = locks[id].writeLock();
            w.lock();
            try {
                final EventConsumer<?>[] oldArray = listeners[id];
                final EventConsumer<?>[] newArray = Arrays.copyOf(oldArray, oldArray.length + 1);
                newArray[oldArray.length] = consumer;
                Arrays.sort(newArray, Comparator.comparingInt(EventConsumer::getPriority));
                listeners[id] = newArray;
            } finally {
                w.unlock();
            }
        }
    }

    @Override
    public <T> void internalUnregister(final Class<T> eventType, final EventConsumer<?> consumer) {
        final int id = REGISTRY.getEventId(eventType);
        if (id > -1) {
            final ReentrantReadWriteLock.WriteLock w = locks[id].writeLock();
            w.lock();
            try {
                final EventConsumer<?>[] oldArray = listeners[id];
                int i = Arrays.asList(oldArray).indexOf(consumer);
                if (i < 0) {
                    return;
                }

                final EventConsumer<?>[] newArray = new EventConsumer<?>[oldArray.length - 1];
                System.arraycopy(oldArray, 0, newArray, 0, i);
                System.arraycopy(oldArray, i + 1, newArray, i, oldArray.length - i - 1);
                listeners[id] = newArray;
            } finally {
                w.unlock();
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> EventWrapper<T> getWrapper(final Class<T> eventType) {
        return (EventWrapper<T>) wrappers.get(eventType);
    }

}
