package bot.staro.rokit;

public interface Invoker<E> {
    void call(Object listener, E event, Object[] wrapped, Object[] provided);

}
