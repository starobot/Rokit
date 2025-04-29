package bot.staro.rokit;

/**
 * A central bus for managing events and subscribers.
 * An event is a generic object that is to be dispatched to the registered listeners.
 * A listener is any object that receives events.
 */
public interface EventBus {
    /**
     * Dispatches an event to all registered consumers.
     * @param event is a generic object that is to be dispatched.
     * @param <E> is a generic type.
     */
    <E> void post(E event);

    /**
     * Subscribes a listener object by delegating to the compile-time-generated registAry.
     * @param subscriber is an object that is being subscribed for receiving listeners.
     */
    void subscribe(Object subscriber);

    /**
     * Unsubscribes by delegating to the generated registry.
     * @param subscriber is an object that is being subscribed for receiving listeners.
     */
    void unsubscribe(Object subscriber);

    /**
     * Checks if the given listener is being currently subscribed to the event bus.
     * @param subscriber is an object that is being subscribed for receiving listeners.
     * @return true if it is currently subscribed to receives events, false otherwise.
     */
    boolean isSubscribed(Object subscriber);

}