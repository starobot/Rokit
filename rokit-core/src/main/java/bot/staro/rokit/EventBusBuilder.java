package bot.staro.rokit;

import java.util.HashMap;
import java.util.Map;

// not final in case anyone decides to extend it.
public class EventBusBuilder extends EventRegistry implements EventBus {
    protected EventBusBuilder() {
        super();
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

    public static final class Builder {
        private final Map<Class<?>,EventWrapper<?>> wrappers = new HashMap<>();

        public <T> Builder wrap(Class<T> eventType, EventWrapper<T> wrapper) {
            wrappers.put(eventType, wrapper);
            return this;
        }

        public <T> Builder wrapSingle(Class<T> eventType, SingleEventWrapper<T> wrapper) {
            wrappers.put(eventType, wrapper);
            return this;
        }

        public EventBusBuilder build() {
            EventBusBuilder bus = new EventBusBuilder();
            bus.wrappers.putAll(wrappers);
            return bus;
        }
    }

}
