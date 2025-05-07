package bot.staro.rokit;

import java.util.IdentityHashMap;
import java.util.Map;

public class Builder {
    protected final Map<Class<?>, EventWrapper<?>> wrappers;

    protected Builder() {
        wrappers = new IdentityHashMap<>();
    }

    public <T> Builder wrap(final Class<T> eventType, final EventWrapper<T> wrapper) {
        wrappers.put(eventType, wrapper);
        return this;
    }

    public <T> Builder wrap(final Class<T> eventType, final SingleEventWrapper<T> wrapper) {
        wrappers.put(eventType, wrapper);
        return this;
    }

    public RokitEventBus build() {
        RokitEventBus bus = new RokitEventBus();
        bus.wrappers.putAll(wrappers);
        return bus;
    }

}
