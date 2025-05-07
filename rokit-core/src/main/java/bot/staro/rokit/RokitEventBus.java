package bot.staro.rokit;

// not final in case anyone decides to extend it.
public class RokitEventBus extends EventRegistry implements EventBus {
    protected RokitEventBus() {
    }

    @Override
    public <E> void post(final E event) {
        super.post(event);
    }

    @Override
    public void subscribe(final Object subscriber) {
        super.subscribe(subscriber);
    }

    @Override
    public void unsubscribe(final Object subscriber) {
        super.unsubscribe(subscriber);
    }

    @Override
    public boolean isSubscribed(final Object subscriber) {
        return super.isSubscribed(subscriber);
    }

    public static Builder builder() {
        return new Builder();
    }

}
