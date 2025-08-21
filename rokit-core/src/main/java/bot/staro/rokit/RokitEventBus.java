package bot.staro.rokit;

import bot.staro.rokit.gen.BusState;
import bot.staro.rokit.generated.GeneratedBootstrap;

import java.util.Objects;
import java.util.function.Supplier;

public final class RokitEventBus implements EventBus {
    private final Object[] providers;
    private final BusState state;

    RokitEventBus(final Object[] providers) {
        this.providers = Objects.requireNonNull(providers, "providers");
        this.state = GeneratedBootstrap.newState();
    }

    @Override
    public <E> void post(final E event) {
        if (event != null) {
            GeneratedBootstrap.dispatch(this, state, event);
        }
    }

    @Override
    public <E> void post(final E event, final int id) {
        if (event != null) {
            GeneratedBootstrap.dispatchById(this, state, event, id);
        }
    }

    @Override
    public void subscribe(final Object subscriber) {
        if (subscriber != null) {
            GeneratedBootstrap.register(this, state, subscriber);
        }
    }

    @Override
    public void unsubscribe(final Object subscriber) {
        if (subscriber != null) {
            GeneratedBootstrap.unregister(this, state, subscriber);
        }
    }

    @Override
    public boolean isSubscribed(final Object subscriber) {
        if (subscriber != null) {
            return GeneratedBootstrap.isSubscribed(state, subscriber);
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    public <T> T getProvider(final int providerId) {
        final Object slot = providers[providerId];
        if (slot != null) {
            return ((Supplier<T>) slot).get();
        }

        return null;
    }

    public static Builder builder() {
        return new Builder();
    }

}
