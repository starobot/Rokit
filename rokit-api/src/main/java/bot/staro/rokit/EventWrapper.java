package bot.staro.rokit;

/**
 * A functional interface representing a wrapper for handling events of type {@code T}.
 * <p>This interface defines a method to process an event and extract values from it.
 * Implementation of this interface will provide the specific logic for handling
 * different types of events.</p>
 * @param <T> the type of the event to be handled.
 */
@FunctionalInterface
public interface EventWrapper<T> {
    /**
     * Handles provided event and returns objects based on the implementation.
     * @param event the event to be handled.
     * @return objects representing the result of handling the event.
     */
    Object[] wrap(T event);

}