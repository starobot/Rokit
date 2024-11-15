package bot.staro.rokit.impl;

import bot.staro.rokit.EventBus;
import bot.staro.rokit.EventListener;
import bot.staro.rokit.SubscriberObject;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBusImpl implements EventBus {
    private final Map<Class<?>, List<EventListener<?>>> listeners = new ConcurrentHashMap<>();

    @Override
    public void post(Object event) {
        Optional.ofNullable(listeners.get(event.getClass()))
                .ifPresent(eventListeners -> eventListeners.forEach(listener -> invokeListener(listener, event)));
    }

    @Override
    public void post(Object event, Class<?> generics) {
        Optional.ofNullable(listeners.get(event.getClass()))
                .ifPresent(eventListeners -> eventListeners.stream()
                        .filter(listener -> listener.getGenericType() == null || listener.getGenericType() == generics)
                        .forEach(listener -> invokeListener(listener, event)));
    }

    @Override
    public void subscribe(Object subscriber) {
        if (subscriber instanceof SubscriberObject subscriberObject) {
            subscriberObject.getListeners().forEach(listener -> {
                listeners.computeIfAbsent(listener.getType(), k -> Collections.synchronizedList(new CopyOnWriteArrayList<>())).add(listener);
            });
        }
    }

    @Override
    public void unsubscribe(Object subscriber) {
        if (subscriber instanceof SubscriberObject subscriberObject) {
            subscriberObject.getListeners().forEach(listener -> {
                Optional.ofNullable(listeners.get(listener.getType()))
                        .ifPresent(eventListeners -> eventListeners.remove(listener));
            });
        }
    }

    @Override
    public boolean isSubscribed(Object subscriber) {
        if (subscriber instanceof SubscriberObject subscriberObject) {
            return subscriberObject.getListeners().stream().anyMatch(listener -> Optional.ofNullable(listeners.get(listener.getType()))
                    .map(eventListeners -> eventListeners.contains(listener))
                    .orElse(false));
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private <T> void invokeListener(EventListener<?> eventListener, Object event) {
        EventListener<T> typedEventListener = (EventListener<T>) eventListener;
        typedEventListener.invoke((T) event);
    }

}
