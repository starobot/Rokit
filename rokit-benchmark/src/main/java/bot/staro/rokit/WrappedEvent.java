package bot.staro.rokit;

public final class WrappedEvent<E> {
    private final E object;

    public WrappedEvent(E object) {
        this.object = object;
    }

    public E getObject() {
        return object;
    }

}
