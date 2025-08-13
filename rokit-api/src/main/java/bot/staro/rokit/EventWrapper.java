package bot.staro.rokit;

/**
 * A functional interface representing a wrapper for handling events of type {@code T}.
 * <p>This interface defines a method to process an event and extract values from it.
 * Implementation of this interface will provide the specific logic for handling
 * different types of events.</p>
 * @param <E> the event to be handled.
 */
public interface EventWrapper<E> {
    int arity();

    void wrapInto(E event, Object[] out);

}