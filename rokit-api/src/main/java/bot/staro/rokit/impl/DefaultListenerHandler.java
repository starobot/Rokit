// file: rokit-api/src/main/java/bot/staro/rokit/impl/DefaultListenerHandler.java
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
    public <E> EventConsumer<E> createConsumer(
            ListenerRegistry bus,
            Object listenerInstance,
            Method method,
            int priority,
            Class<E> eventType
    ) {
        // figure out how many parameters the method expects
        int paramCount = method.getParameterCount();

        EventConsumer<E> consumer = new EventConsumer<>() {
            @Override
            public void accept(E event) {
                try {
                    if (paramCount == 1) {
                        // simple single-arg listener
                        method.invoke(listenerInstance, event);
                    } else {
                        // lookup wrapper for this event type
                        @SuppressWarnings("unchecked")
                        EventWrapper<E> wrapper = (EventWrapper<E>) bus.getWrapper(eventType);
                        if (wrapper == null) {
                            // no wrapper registered → skip
                            return;
                        }

                        Object[] extras = wrapper.wrap(event);
                        // must match paramCount − 1 extras
                        if (extras.length != paramCount - 1) {
                            return;
                        }

                        // build argument array: [event, extras...]
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

        // register on the bus, so bus.post() will call it
        bus.internalRegister(eventType, consumer);
        return consumer;
    }
}
