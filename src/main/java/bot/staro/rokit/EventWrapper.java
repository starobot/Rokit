package bot.staro.rokit;

/**
 * A functional interface representing a wrapper for handling events of type {@code T}.
 *
 * <p>This interface defines a method to process an event and extract a value from it.
 * Implementation of this interface will provide the specific logic for handling
 * different types of events.</p>
 *
 * @param <T> the type of the event to be handled
 */

@FunctionalInterface
public interface EventWrapper<T> {
    /**
     * Handles provided event and returns an object based on the implementation.
     *
     * @param event the event to be handled
     * @return an object representing the result of handling the event
     */
    Object handle(T event);

    /**
     * Invokes the {@link #handle(Object)} method with the specified object.
     * This default method allows for handling an event of a generic type by
     * casting the input object to the expected type {@code T}.
     *
     * @param o the object to be handled, which is expected to be of type {@code T}
     * @return an object representing the result of handling the event
     */
    @SuppressWarnings("unchecked")
    default Object invoke(Object o) {
        return handle((T) o);
    }

}
