package bot.staro.rokit;

public interface EventBus {
    void post(Object event);

    void post(Object event, Class<?> generics);

    void subscribe(Object subscriber);

    void unsubscribe(Object subscriber);

    boolean isSubscribed(Object subscriber);

}
