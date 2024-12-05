package bot.staro.rokit.function;

/**
 * An extension of the {@link EventWrapper} that handles single objects.
 *
 * @param <T> the type of the event to be handled.
 */

@FunctionalInterface
public interface SingleEventWrapper<T> extends EventWrapper<T> {
    /**
     * Handles provided event and returns an object based on the implementation.
     *
     * @param event the event to be handled.
     * @return an object representing the result of handling the event.
     */
    Object handleSingle(T event);

    default Object[] handle(T event) {
        return new Object[] {handleSingle(event)};
    }

}
