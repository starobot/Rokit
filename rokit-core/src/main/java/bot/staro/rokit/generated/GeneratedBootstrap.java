package bot.staro.rokit.generated;

import bot.staro.rokit.RokitEventBus;
import bot.staro.rokit.gen.BusState;

/**
 * Generated bootstrap. Is overridden upon compilation by the annotation processor.
 */
public final class GeneratedBootstrap {
    private GeneratedBootstrap() { }

    public static BusState newState() {
        throw new UnsupportedOperationException("Generated at compile time");
    }

    public static void register(final RokitEventBus bus, final BusState state, final Object subscriber) {
        throw new UnsupportedOperationException("Generated at compile time");
    }

    public static void unregister(final RokitEventBus bus, final BusState state, final Object subscriber) {
        throw new UnsupportedOperationException("Generated at compile time");
    }

    public static boolean isSubscribed(final BusState state, final Object subscriber) {
        throw new UnsupportedOperationException("Generated at compile time");
    }

    public static <E> void dispatch(final RokitEventBus bus, final BusState state, final E event) {
        throw new UnsupportedOperationException("Generated at compile time");
    }

    public static <E> void dispatchById(final RokitEventBus bus, final BusState state, final E event, final int id) {
        throw new UnsupportedOperationException("Generated at compile time");
    }

}
