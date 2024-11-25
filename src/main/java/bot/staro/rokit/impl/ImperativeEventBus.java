package bot.staro.rokit.impl;

import bot.staro.rokit.EventBus;
import bot.staro.rokit.EventListener;
import bot.staro.rokit.Listener;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

public class ImperativeEventBus implements EventBus {
    private final Map<Class<?>, List<EventListener>> listeners = new ConcurrentHashMap<>();
    private final Map<Object, List<Class<?>>> subscriptions = new ConcurrentHashMap<>();
    private final Map<Class<? extends Annotation>, BiFunction<Object, Method, EventListener>> listenerFactories = new HashMap<>();

    public ImperativeEventBus() {
        registerListenerFactory(Listener.class, (instance, method) -> new EventListenerImpl(instance, method, method.getAnnotation(Listener.class).priority().getVal()));
    }

    @Override
    public void post(Object event) {
        List<EventListener> listeners = this.listeners.get(event.getClass());
        if (listeners == null) {
            return;
        }

        for (EventListener l : listeners) {
            if (l.getInstance() == null) {
                return;
            }

            Class<?> eventParamType = l.getMethod().getParameterTypes()[0];
            if (eventParamType.isAssignableFrom(event.getClass())) {
                l.invoke(event);
            }
        }
    }

    @Override
    public void subscribe(Object instance) {
        List<Method> methods = getListeningMethods(instance.getClass());
        List<Class<?>> subscribedEvents = subscriptions.computeIfAbsent(instance, k -> new ArrayList<>());
        for (Method method : methods) {
            Class<?> eventType = getEventParameterType(method);
            listeners.putIfAbsent(eventType, new CopyOnWriteArrayList<>());
            List<EventListener> list = listeners.get(eventType);
            for (Annotation annotation : method.getAnnotations()) {
                if (listenerFactories.containsKey(annotation.annotationType())) {
                    BiFunction<Object, Method, EventListener> factory = listenerFactories.get(annotation.annotationType());
                    EventListener listener = factory.apply(instance, method);
                    list.add(listener);
                    list.sort(Comparator.comparingInt(EventListener::getPriority).reversed());
                    subscribedEvents.add(eventType);
                    break;
                }
            }
        }
    }

    @Override
    public void unsubscribe(Object instance) {
        List<Method> methods = getListeningMethods(instance.getClass());
        for (Method method : methods) {
            Class<?> eventType = getEventParameterType(method);
            List<EventListener> list = listeners.get(eventType);
            if (list == null) {
                continue;
            }

            list.removeIf(l -> l.getMethod().equals(method) && l.getInstance() == instance);
            subscriptions.remove(instance);
        }
    }

    @Override
    public boolean isSubscribed(Object instance) {
        return false;
    }

    @Override
    public void registerListenerFactory(Class<? extends Annotation> annotationType, BiFunction<Object, Method, EventListener> factory) {
        listenerFactories.put(annotationType, factory);
    }

    private List<Method> getListeningMethods(Class<?> clazz) {
        ArrayList<Method> listening = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            for (Annotation annotation : method.getDeclaredAnnotations()) {
                if (listenerFactories.containsKey(annotation.annotationType()) && method.getParameterCount() == 1) {
                    listening.add(method);
                }
            }
        }

        return listening;
    }

    private static Class<?> getEventParameterType(Method method) {
        if (method.getParameterCount() != 1) {
            return null;
        }

        return method.getParameters()[0].getType();
    }

}
