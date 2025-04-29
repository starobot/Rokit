package bot.staro.rokit.impl;

import bot.staro.rokit.AnnotationHandler;
import bot.staro.rokit.EventConsumer;
import bot.staro.rokit.EventWrapper;
import bot.staro.rokit.ListenerRegistry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Fallback handler for any listener annotation that isn't inlined.
 * Supports multi-arg wrapped listeners by using the bus's EventWrapper.
 */
public record DefaultListenerHandler() implements AnnotationHandler {
    @Override
    public <E> EventConsumer<E> createConsumer(ListenerRegistry bus, Object listenerInstance, Method method, int priority, Class<E> eventType) {
        int paramCount = method.getParameterCount();
        EventConsumer<E> consumer = new EventConsumer<>() {
            @Override
            public void accept(E event) {
                try {
                    if (paramCount == 1) {
                        method.invoke(listenerInstance, event);
                    } else {
                        EventWrapper<E> wrapper = bus.getWrapper(eventType);
                        if (wrapper == null) {
                            return;
                        }

                        Object[] extras = wrapper.wrap(event);
                        if (extras.length != paramCount - 1) {
                            return;
                        }

                        Object[] args = new Object[paramCount];
                        args[0] = event;
                        System.arraycopy(extras, 0, args, 1, extras.length);

                        method.invoke(listenerInstance, args);
                    }
                } catch (IllegalAccessException | InvocationTargetException ex) {
                    throw new RuntimeException("Failed to invoke listener", ex);
                }
            }

            @Override
            public Object getInstance() {
                return listenerInstance;
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

        bus.internalRegister(eventType, consumer);
        return consumer;
    }

}
