package bot.staro.rokit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class Builder {
    private final List<Object> providers = new ArrayList<>();

    public <T> Builder withProvider(final int providerId, final Supplier<T> supplier) {
        while (providers.size() < providerId + 1) {
            providers.add(null);
        }

        providers.set(providerId, supplier);
        return this;
    }

    public EventBus build() {
        return new RokitEventBus(providers.toArray());
    }

}
