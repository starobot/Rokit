package bot.staro.rokit;

import java.lang.reflect.Method;

/**
 * Represents an event listener associated with a method.
 * Encapsulates the logic for invoking the method and storing its metadata.
 */
public interface EventListener {
    /**
     * Invokes the listener with the given event upon the event dispatching in {@link EventBus}
     *
     * @param event the event object to handle.
     */
    void invoke(Object event);

    /**
     * Gets the instance containing the listener method.
     *
     * @return the object instance.
     */
    Object getInstance();

    /**
     * Gets the method associated with this listener.
     *
     * @return the listener method.
     */
    Method getMethod();

    /**
     * Gets the priority of this listener.
     *
     * @return the priority value.
     */
    int getPriority();

}
