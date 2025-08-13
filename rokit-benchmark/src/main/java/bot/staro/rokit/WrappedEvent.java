package bot.staro.rokit;

public final class WrappedEvent<E extends String> {
    private final E object;

    public WrappedEvent(E object) {
        this.object = object;
    }

    public E getObject() {
        return object;
    }

}
