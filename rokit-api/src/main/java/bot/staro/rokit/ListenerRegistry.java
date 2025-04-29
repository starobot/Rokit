package bot.staro.rokit;

/**
 * The processor will generate calls to this to register consumers.
 */
public interface ListenerRegistry {
    <T> EventWrapper<T> getWrapper(Class<T> eventType);

    <T> void internalRegister(Class<T> eventType, EventConsumer<?> consumer);

    <T> void internalUnregister(Class<T> eventType, EventConsumer<?> consumer);

}
