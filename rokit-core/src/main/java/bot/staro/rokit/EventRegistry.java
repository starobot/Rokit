package bot.staro.rokit;

import bot.staro.rokit.gen.GeneratedRegistry;

import java.util.Map;
import java.util.ServiceLoader;

public class EventRegistry implements ListenerRegistry {
    protected static final GeneratedRegistry REGISTRY = ServiceLoader.load(GeneratedRegistry.class)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No Rokit listener registry found on class-path"));
    private static final EventConsumer<?>[] EMPTY = new EventConsumer<?>[0];

    private final Map<Class<?>, EventWrapper<?>> wrappers;
    private final ListenerSlot[] listenerSlots;

    public EventRegistry(final Map<Class<?>, EventWrapper<?>> wrappers) {
        this.wrappers = wrappers;
        final Class<?>[] types = REGISTRY.eventTypes();
        this.listenerSlots = new ListenerSlot[types.length];
        for (int i = 0; i < types.length; i++) {
            this.listenerSlots[i] = new ListenerSlot();
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    @Override
    public final <T> void internalRegister(final Class<T> eventType, final EventConsumer<?> consumer) {
        final int id = REGISTRY.getEventId(eventType);
        if (id < 0) {
            return;
        }

        final ListenerSlot slot = listenerSlots[id];
        synchronized (slot) {
            final EventConsumer<?>[] previousArray = slot.array;
            final int n = previousArray.length;
            final EventConsumer<?>[] newArray = new EventConsumer<?>[n + 1];

            final int p = consumer.getPriority();
            int lo = 0;
            int hi = n;
            while (lo < hi) {
                final int mid = (lo + hi) >>> 1;
                if (previousArray[mid].getPriority() > p) {
                    hi = mid;
                } else {
                    lo = mid + 1;
                }
            }

            System.arraycopy(previousArray, 0, newArray, 0, lo);
            newArray[lo] = consumer;
            System.arraycopy(previousArray, lo, newArray, lo + 1, n - lo);
            slot.array = newArray;
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    @Override
    public final <T> void internalUnregister(final Class<T> eventType, final EventConsumer<?> consumer) {
        final int id = REGISTRY.getEventId(eventType);
        if (id < 0) {
            return;
        }

        final ListenerSlot slot = listenerSlots[id];
        synchronized (slot) {
            final EventConsumer<?>[] previousArray = slot.array;
            int i = -1;
            for (int k = previousArray.length - 1; k >= 0; k--) {
                if (previousArray[k] == consumer) {
                    i = k;
                    break;
                }
            }

            if (i < 0) {
                return;
            }

            final int n = previousArray.length - 1;
            if (n == 0) {
                slot.array = EMPTY;
                return;
            }

            final EventConsumer<?>[] newArray = new EventConsumer<?>[n];
            System.arraycopy(previousArray, 0, newArray, 0, i);
            System.arraycopy(previousArray, i + 1, newArray, i, n - i);
            slot.array = newArray;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T> EventWrapper<T> getWrapper(final Class<T> eventType) {
        return (EventWrapper<T>) wrappers.get(eventType);
    }

    @SuppressWarnings("unchecked")
    protected final <E> EventConsumer<E>[] listenersForId(final int id) {
        return (EventConsumer<E>[]) listenerSlots[id].array;
    }

    private static final class ListenerSlot {
        volatile EventConsumer<?>[] array = EMPTY;
    }

}
