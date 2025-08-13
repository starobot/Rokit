package bot.staro.rokit;

import java.util.IdentityHashMap;
import java.util.Map;

public final class Builder {
    private final Map<Class<?>, EventWrapper<?>> wrappers = new IdentityHashMap<>();

    public <T> Builder wrap(final Class<T> eventType, final EventWrapper<T> wrapper) {
        wrappers.put(eventType, wrapper);
        return this;
    }

    public <T> Builder wrap(final Class<T> eventType, final SingleEventWrapper<T> wrapper) {
        wrappers.put(eventType, wrapper);
        return this;
    }

    public RokitEventBus build() {
        return new RokitEventBus(Map.copyOf(wrappers));
    }

}
