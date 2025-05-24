package bot.staro.rokit.impl;

import bot.staro.rokit.EventConsumer;
import bot.staro.rokit.EventWrapper;
import bot.staro.rokit.ListenerRegistry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Base class for all run-time generated EventConsumers produced by a handler.
 * @param <E> is a generic event.
 */
public abstract class BaseEventConsumer<E> implements EventConsumer<E> {
    protected final ListenerRegistry bus;
    protected final Object listener;
    protected final Method method;
    protected final int priority;
    protected final Class<E> eventType;
    private final int paramCount;

    protected BaseEventConsumer(ListenerRegistry bus, Object listener, Method method, int priority, Class<E> eventType) {
        this.bus = bus;
        this.listener = listener;
        this.method = method;
        this.priority = priority;
        this.eventType = eventType;
        this.paramCount = method.getParameterCount();
    }

    @Override
    public final void accept(final E event) {
        if (!preInvoke(event)) {
            return;
        }

        try {
            invoke(event);
            postInvoke(event);
        } catch (Throwable t) {
            handleException(event, t);
        }
    }

    // to be overridden
    protected boolean preInvoke(E event) {
        return true;
    }

    // to be overridden
    protected void postInvoke(E event) {
    }

    protected void handleException(E event, Throwable t) {
        throw (t instanceof InvocationTargetException ite)
                ? new RuntimeException("Listener threw", ite.getCause())
                : new RuntimeException("Failed to invoke listener", t);
    }

    private void invoke(final E event) throws Throwable {
        if (paramCount == 1) {
            method.invoke(listener, event);
            return;
        }

        final EventWrapper<E> w = bus.getWrapper(eventType);
        if (w == null) {
            return;
        }

        final Object[] extras = w.wrap(event);
        if (extras.length != paramCount - 1) {
            return;
        }

        final Object[] args = new Object[paramCount];
        args[0] = event;
        System.arraycopy(extras, 0, args, 1, extras.length);
        if (validateTypes(args)) {
            method.invoke(listener, args);
        }
    }

    private boolean validateTypes(final Object[] args) {
        final Class<?>[] types = method.getParameterTypes();
        for (int i = 1; i < types.length; i++) {
            final Object a = args[i];
            if (a != null && !types[i].isInstance(a)) {
                return false;
            }
        }

        return true;
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
