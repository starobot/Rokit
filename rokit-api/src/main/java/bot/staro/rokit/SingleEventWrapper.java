package bot.staro.rokit;

/**
 * An extension of the {@link EventWrapper} that handles single objects.
 * @param <T> the type of the event to be handled.
 */
@FunctionalInterface
public interface SingleEventWrapper<T> extends EventWrapper<T> {
    /**
     * Unwraps an event to a single object.
     * @param event the event to be unwrapped.
     * @return an object extracted from the event.
     */
    Object unwrap(T event);

    @Override
    default Object[] wrap(T event) {
        return new Object[]{ unwrap(event) };
    }

}