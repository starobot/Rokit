package bot.staro.rokit.impl;

import bot.staro.rokit.*;

public final class DefaultListenerHandler implements AnnotationHandler {
    public static final Object[] EMPTY = new Object[0];

    @Override
    public <E> EventConsumer<E> createConsumer(final ListenerRegistry bus, final Object listenerInstance, final Invoker<E> invoker,
                                               final int priority, final Class<E> eventType, final int wrappedCount, final ArgProvider<? super E>[] providers) {
        return new EventConsumer<>() {
            final EventWrapper<E> w0 = wrappedCount == 0 ? null : bus.getWrapper(eventType);
            final Object[] wrappedBuf = wrappedCount == 0 ? null : new Object[wrappedCount];
            final Object[] providedBuf = providers.length == 0 ? null : new Object[providers.length];

            @Override
            public void accept(final E e) {
                if (wrappedCount == 0) {
                    if (providedBuf != null) {
                        for (int i = 0; i < providers.length; i++) {
                            providedBuf[i] = providers[i].get(bus, listenerInstance, e);
                        }

                        invoker.call(listenerInstance, e, EMPTY, providedBuf);
                    } else {
                        invoker.call(listenerInstance, e, EMPTY, EMPTY);
                    }

                    return;
                }

                if (w0 == null) {
                    return;
                }

                w0.wrapInto(e, wrappedBuf);
                if (providedBuf != null) {
                    for (int i = 0; i < providers.length; i++) {
                        providedBuf[i] = providers[i].get(bus, listenerInstance, e);
                    }

                    invoker.call(listenerInstance, e, wrappedBuf, providedBuf);
                } else {
                    invoker.call(listenerInstance, e, wrappedBuf, EMPTY);
                }
            }

            @Override
            public int getPriority() {
                return priority;
            }

            @Override
            public Class<E> getEventType() {
                return eventType;
            }
        };
    }

}
