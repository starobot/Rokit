package bot.staro.rokit;

public interface AnnotationHandler {
    <E> EventConsumer<E> createConsumer(ListenerRegistry bus, Object listenerInstance, Invoker<E> invoker, int priority, Class<E> eventType, int wrappedCount, ArgProvider<? super E>[] providers);

}
