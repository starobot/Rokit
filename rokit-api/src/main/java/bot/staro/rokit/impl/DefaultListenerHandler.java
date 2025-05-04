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
public class DefaultListenerHandler implements AnnotationHandler {
    @Override
    public <E> EventConsumer<E> createConsumer(ListenerRegistry bus, Object listenerInstance, Method method, int priority, Class<E> eventType) {
        EventConsumer<E> consumer = createConsumerInstance(bus, listenerInstance, method, priority, eventType);
        bus.internalRegister(eventType, consumer);
        return consumer;
    }

    protected <E> EventConsumer<E> createConsumerInstance(ListenerRegistry bus, Object listenerInstance, Method method, int priority, Class<E> eventType) {
        return new DefaultEventConsumer<>(bus, listenerInstance, method, priority, eventType);
    }

    /**
     * Default event consumer, that handles any types of events.
     * Pre- and Post-hooks are made in order to potentially extend the default functionality by overriding those methods.
     * @param <E> is the event type.
     */
    protected static class DefaultEventConsumer<E> implements EventConsumer<E> {
        protected final ListenerRegistry bus;
        protected final Object listenerInstance;
        protected final Method method;
        protected final int priority;
        protected final Class<E> eventType;
        protected final int paramCount;

        public DefaultEventConsumer(ListenerRegistry bus, Object listenerInstance, Method method, int priority, Class<E> eventType) {
            this.bus = bus;
            this.listenerInstance = listenerInstance;
            this.method = method;
            this.priority = priority;
            this.eventType = eventType;
            this.paramCount = method.getParameterCount();
        }

        @Override
        public void accept(E event) {
            if (!preInvoke(event)) {
                return;
            }

            try {
                invokeMethod(event);
                postInvoke(event);
            } catch (Exception ex) {
                handleException(event, ex);
            }
        }

        /**
         * Called before method invocation.
         */
        protected boolean preInvoke(E event) {
            return true;
        }

        /**
         * Called after successful method invocation.
         */
        protected void postInvoke(E event) {
        }

        protected void handleException(E event, Exception ex) {
            if (ex instanceof InvocationTargetException ite) {
                throw new RuntimeException("Failed to invoke listener", ite.getCause());
            } else {
                throw new RuntimeException("Failed to invoke listener", ex);
            }
        }

        protected void invokeMethod(E event) throws IllegalAccessException, InvocationTargetException {
            if (paramCount == 1) {
                method.invoke(listenerInstance, event);
            } else {
                invokeWithWrappedArgs(event);
            }
        }

        protected void invokeWithWrappedArgs(E event) throws IllegalAccessException, InvocationTargetException {
            EventWrapper<E> wrapper = bus.getWrapper(eventType);
            if (wrapper != null) {
                Object[] extras = wrapper.wrap(event);
                if (extras.length != paramCount - 1) {
                    return;
                }

                Object[] args = new Object[paramCount];
                args[0] = event;
                System.arraycopy(extras, 0, args, 1, extras.length);
                if (validateArgTypes(args)) {
                    method.invoke(listenerInstance, args);
                }
            }
        }

        protected boolean validateArgTypes(Object[] args) {
            Class<?>[] paramTypes = method.getParameterTypes();
            for (int i = 1; i < paramTypes.length; i++) {
                Object a = args[i];
                if (a != null && !paramTypes[i].isInstance(a)) {
                    return false;
                }
            }

            return true;
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
    }

}
