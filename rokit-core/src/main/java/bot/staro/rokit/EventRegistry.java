package bot.staro.rokit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Reflective scanning for now until I figure out something better.
public class EventRegistry implements ListenerRegistry {
    protected final Map<Class<?>,List<EventConsumer<?>>> listeners = new ConcurrentHashMap<>();
    protected final Map<Class<?>,EventWrapper<?>> wrappers = new HashMap<>();
    private final RegistryInvoker invoker = findRegistry();

    protected EventRegistry() {
    }

    @SuppressWarnings("unchecked")
    protected <E> void post(E event) {
        List<EventConsumer<?>> list = listeners.get(event.getClass());
        if (list != null) {
            for (EventConsumer<?> c : list) {
                ((EventConsumer<E>) c).accept(event);
            }
        }
    }

    protected void subscribe(Object subscriber) {
        invoker.register(this, subscriber);
    }

    protected void unsubscribe(Object subscriber) {
        invoker.unregister(this, subscriber);
    }

    protected boolean isSubscribed(Object subscriber) {
        return invoker.isSubscribed(subscriber);
    }

    public <T> void internalRegister(Class<T> eventType, EventConsumer<?> c) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>())
                .add(c);
        listeners.get(eventType).sort(Comparator.<EventConsumer<?>>comparingInt(EventConsumer::getPriority)
                .reversed());
    }

    public <T> void internalUnregister(Class<T> eventType, EventConsumer<?> c) {
        List<EventConsumer<?>> list = listeners.get(eventType);
        if (list != null) {
            list.remove(c);
            if (list.isEmpty()) {
                listeners.remove(eventType);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> EventWrapper<T> getWrapper(Class<T> eventType) {
        return (EventWrapper<T>) wrappers.get(eventType);
    }

    private static RegistryInvoker findRegistry() {
        try {
            Class<?> registry = Class.forName("bot.staro.rokit.generated.EventListenerRegistry");
            Method register   = registry.getMethod("register", EventRegistry.class, Object.class);
            Method unregister = registry.getMethod("unregister", EventRegistry.class, Object.class);
            Field subsField  = registry.getField("SUBSCRIBERS");
            return new RegistryInvoker(register, unregister, subsField);
        } catch (Exception e) {
            return new RegistryInvoker();
        }
    }

    private static final class RegistryInvoker {
        private final Method register;
        private final Method unregister;
        private final Field subscribers;

        private RegistryInvoker(Method register, Method unregister, Field subscribers) {
            this.register = register;
            this.unregister = unregister;
            this.subscribers = subscribers;
        }

        private RegistryInvoker() {
            this(null, null, null);
        }

        boolean isNoop() {
            return register == null;
        }

        void register(EventRegistry bus, Object sub) {
            if (!isNoop()) {
                try {
                    register.invoke(null, bus, sub);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        void unregister(EventRegistry bus, Object sub) {
            if (!isNoop()) {
                try {
                    // this is bad.
                    assert unregister != null;
                    unregister.invoke(null, bus, sub);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        boolean isSubscribed(Object sub) {
            if (isNoop()) {
                return false;
            }

            try {
                // this is bad.
                assert subscribers != null;
                @SuppressWarnings("unchecked")
                Map<Object,List<?>> map = (Map<Object,List<?>>)subscribers.get(null);
                return map.containsKey(sub);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}
