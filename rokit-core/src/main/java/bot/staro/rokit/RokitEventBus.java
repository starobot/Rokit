package bot.staro.rokit;

// not final in case anyone decides to extend it.
public class RokitEventBus extends EventRegistry implements EventBus {
    protected RokitEventBus() {
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <E> void post(final E event) {
        final int id = REGISTRY.getEventId(event.getClass());
        if (id >= 0) {
            final EventConsumer<E>[] arr = (EventConsumer<E>[]) listenerArrays[id];
            int i = arr.length;
            while (i-- != 0) {
                arr[i].accept(event);
            }
        }
    }

    @Override
    public void subscribe(final Object subscriber) {
        if (!isSubscribed(subscriber)) {
            REGISTRY.register(this, subscriber);
        }
    }

    @Override
    public void unsubscribe(final Object subscriber) {
        if (isSubscribed(subscriber)) {
            REGISTRY.unregister(this, subscriber);
        }
    }

    @Override
    public boolean isSubscribed(final Object subscriber) {
        return REGISTRY.subscribers().containsKey(subscriber);
    }

    public static Builder builder() {
        return new Builder();
    }

}
