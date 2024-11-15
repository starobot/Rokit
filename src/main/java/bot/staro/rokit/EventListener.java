package bot.staro.rokit;

public interface EventListener<E> {
    void invoke(E event);

    Class<E> getType();

    default Class<?> getGenericType() {
        return null;
    }

}
