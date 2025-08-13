package bot.staro.rokit;

import java.util.Map;

// not final in case anyone decides to extend it.
public class RokitEventBus extends EventRegistry implements EventBus {
    protected RokitEventBus(final Map<Class<?>, EventWrapper<?>> wrappers) {
        super(wrappers);
    }

    @Override
    public final <E> void post(final E event) {
        final int id = REGISTRY.getEventId(event.getClass());
        if (id >= 0) {
            post(event, id);
        }
    }

    @Override
    public final <E> void post(final E event, final int id) {
        final EventConsumer<E>[] a = listenersForId(id);
        switch (a.length) {
            case 0:
                return;
            case 1:
                a[0].accept(event);
                return;
            case 2:
                a[1].accept(event); a[0].accept(event);
                return;
            default:
                for (int i = a.length; --i >= 0; ) {
                    a[i].accept(event);
                }
        }
    }

    @Override
    public final void subscribe(final Object subscriber) {
        if (!isSubscribed(subscriber)) {
            REGISTRY.register(this, subscriber);
        }
    }

    @Override
    public final void unsubscribe(final Object subscriber) {
        if (isSubscribed(subscriber)) {
            REGISTRY.unregister(this, subscriber);
        }
    }

    @Override
    public final boolean isSubscribed(final Object subscriber) {
        return REGISTRY.subscribers().containsKey(subscriber);
    }

    public static Builder builder() {
        return new Builder();
    }

}
