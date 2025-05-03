package bot.staro.rokit;

import java.util.HashMap;
import java.util.Map;

public final class Builder {
    final Map<Class<?>, EventWrapper<?>> wrappers = new HashMap<>();

    <T> Builder wrap(Class<T> eventType, EventWrapper<T> wrapper) {
        wrappers.put(eventType, wrapper);
        return this;
    }

    <T> Builder wrap(Class<T> eventType, SingleEventWrapper<T> wrapper) {
        wrappers.put(eventType, wrapper);
        return this;
    }

    RokitEventBus build() {
        RokitEventBus bus = new RokitEventBus();
        bus.wrappers.putAll(wrappers);
        return bus;
    }

}
