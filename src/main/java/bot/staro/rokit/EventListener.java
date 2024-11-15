package bot.staro.rokit;

public interface EventListener<E> {
    int DEFAULT_LISTENER_PRIORITY = 0;

    void invoke(E event);

    Class<E> getType();

    default Class<?> getGenericType() {
        return null;
    }

    default int getPriority() {
        return DEFAULT_LISTENER_PRIORITY;
    }

}
