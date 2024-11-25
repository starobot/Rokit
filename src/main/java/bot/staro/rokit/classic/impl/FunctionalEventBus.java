package bot.staro.rokit.classic.impl;

import bot.staro.rokit.classic.EventBus;
import bot.staro.rokit.classic.EventListener;
import bot.staro.rokit.classic.Listener;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class FunctionalEventBus implements EventBus {
    private final Map<Class<?>, List<EventListener>> listeners = new ConcurrentHashMap<>();
    private final Map<Object, Set<Class<?>>> subscriptions = new ConcurrentHashMap<>();
    private final Map<Class<? extends Annotation>, BiFunction<Object, Method, EventListener>> listenerFactories = new HashMap<>();

    public FunctionalEventBus() {
        this.registerListenerFactory(Listener.class, (instance, method) -> new EventListenerImpl(instance, method, method.getAnnotation(Listener.class).priority().getVal()));
    }

    public void post(Object event) {
        listeners.getOrDefault(event.getClass(), Collections.emptyList())
                .forEach(listener -> listener.invoke(event));
    }

    @Override
    public void subscribe(Object instance) {
        getListeningMethods(instance.getClass()).stream()
                .filter(method -> Arrays.stream(method.getAnnotations())
                        .anyMatch(a -> listenerFactories.containsKey(a.annotationType())))
                .forEach(method -> {
                    Annotation annotation = Arrays.stream(method.getAnnotations())
                            .filter(a -> listenerFactories.containsKey(a.annotationType()))
                            .findFirst().orElse(null);
                    if (annotation != null) {
                        Class<?> eventType = method.getParameterTypes()[0];
                        BiFunction<Object, Method, EventListener> factory = listenerFactories.get(annotation.annotationType());
                        EventListener listener = factory.apply(instance, method);
                        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
                        subscriptions.computeIfAbsent(instance, k -> new HashSet<>()).add(eventType);
                    }
                });
    }

    @Override
    public void unsubscribe(Object instance) {
        getListeningMethods(instance.getClass()).forEach(method -> {
            Class<?> eventType = method.getParameterTypes()[0];
            listeners.computeIfPresent(eventType, (k, v) -> v.stream()
                    .filter(listener -> !listener.equals(instance))
                    .collect(Collectors.toList()));
        });
        subscriptions.remove(instance);
    }

    @Override
    public boolean isSubscribed(Object instance) {
        return subscriptions.containsKey(instance);
    }

    @Override
    public void registerListenerFactory(Class<? extends Annotation> annotationType, BiFunction<Object, Method, EventListener> factory) {
        listenerFactories.put(annotationType, factory);
    }

    private List<Method> getListeningMethods(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(method -> method.getParameterCount() == 1 &&
                        Arrays.stream(method.getAnnotations())
                                .anyMatch(annotation -> listenerFactories.containsKey(annotation.annotationType())))
                .collect(Collectors.toList());
    }

}
