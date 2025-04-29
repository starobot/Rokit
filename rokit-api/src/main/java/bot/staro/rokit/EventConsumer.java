package bot.staro.rokit;

/**
 * Represents an event listener associated with a method.
 * Encapsulates the logic for invoking the method and storing its metadata.
 */
public interface EventConsumer<E> {
    /**
     * Invokes the listener with the given event.
     * @param event the event object to handle.
     */
    void accept(E event);

    Object getInstance();

    int getPriority();

    Class<E> getEventType();

}