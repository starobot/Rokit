package bot.staro.rokit;

public interface ArgProvider<E> {
    Object get(ListenerRegistry bus, Object listener, E event);

}
