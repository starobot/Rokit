package bot.staro.rokit.function;

public interface SingleEventWrapper<T> extends EventWrapper<T> {
    default Object[] handle(T event) {
        return new Object[] { handleSingle(event) };
    }

    Object handleSingle(T event);
}
