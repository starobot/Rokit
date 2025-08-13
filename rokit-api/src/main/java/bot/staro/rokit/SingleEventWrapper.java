package bot.staro.rokit;

@FunctionalInterface
public interface SingleEventWrapper<T> extends EventWrapper<T> {
    Object unwrap(T event);

    @Override
    default int arity() {
        return 1;
    }

    @Override
    default void wrapInto(T event, Object[] out) {
        out[0] = unwrap(event);
    }

}
