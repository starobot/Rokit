package bot.staro.rokit.impl;

import bot.staro.rokit.AnnotationHandler;
import bot.staro.rokit.EventConsumer;
import bot.staro.rokit.ListenerRegistry;

import java.lang.reflect.Method;

/**
 * Fallback handler for any listener annotation that isn't inlined.
 * Supports multi-arg wrapped listeners by using the bus's EventWrapper.
 */
public class DefaultListenerHandler implements AnnotationHandler {
    @Override
    public <E> EventConsumer<E> createConsumer(final ListenerRegistry bus, final Object listenerInstance,
                                               final Method method, final int priority, final Class<E> eventType) {
        EventConsumer<E> consumer = new DefaultEventConsumer<>(bus, listenerInstance, method, priority, eventType);
        bus.internalRegister(eventType, consumer);
        return consumer;
    }

    private static final class DefaultEventConsumer<E> extends BaseEventConsumer<E> {
        private DefaultEventConsumer(ListenerRegistry b, Object l, Method m, int p, Class<E> t) {
            super(b, l, m, p, t);
        }
    }

}
