package bot.staro.rokit;

// not final in case anyone decides to extend it.
public class RokitEventBus extends EventRegistry implements EventBus {
    protected RokitEventBus() {
    }

    @Override
    public <E> void post(E event) {
        super.post(event);
    }

    @Override
    public void subscribe(Object subscriber) {
        super.subscribe(subscriber);
    }

    @Override
    public void unsubscribe(Object subscriber) {
        super.unsubscribe(subscriber);
    }

    @Override
    public boolean isSubscribed(Object subscriber) {
        return super.isSubscribed(subscriber);
    }

    public static Builder builder() {
        return new Builder();
    }

}
