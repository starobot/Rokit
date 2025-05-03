package bot.staro.rokit;

import java.util.HashMap;
import java.util.Map;

public class Builder {
    protected final Map<Class<?>, EventWrapper<?>> wrappers = new HashMap<>();

    protected Builder() {
    }

    public <T> Builder wrap(Class<T> eventType, EventWrapper<T> wrapper) {
        wrappers.put(eventType, wrapper);
        return this;
    }

    public <T> Builder wrap(Class<T> eventType, SingleEventWrapper<T> wrapper) {
        wrappers.put(eventType, wrapper);
        return this;
    }

    public RokitEventBus build() {
        RokitEventBus bus = new RokitEventBus();
        bus.wrappers.putAll(wrappers);
        return bus;
    }

}
