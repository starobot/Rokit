package bot.staro.rokit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EventBus {
    private final Map<Class<?>, List<EventListener>> listeners = new ConcurrentHashMap<>();
    private final Map<Object, Set<Class<?>>> subscriptions = new ConcurrentHashMap<>();
    private final Map<Class<? extends Annotation>, BiFunction<Object, Method, EventListener>> listenerFactories = new HashMap<>();

    private EventBus(Map<Class<? extends Annotation>, BiFunction<Object, Method, EventListener>> factories) {
        this.listenerFactories.putAll(factories);
    }

    public void post(Object event) {
        listeners.getOrDefault(event.getClass(), Collections.emptyList())
                .forEach(listener -> listener.invoke(event));
    }

    public void subscribe(Object subscriber) {
        getListeningMethods(subscriber.getClass()).stream()
                .filter(method -> Arrays.stream(method.getAnnotations())
                        .anyMatch(a -> listenerFactories.containsKey(a.annotationType())))
                .forEach(method -> {
                    Annotation annotation = Arrays.stream(method.getAnnotations())
                            .filter(a -> listenerFactories.containsKey(a.annotationType()))
                            .findFirst().orElse(null);
                    if (annotation != null) {
                        Class<?> eventType = method.getParameterTypes()[0];
                        BiFunction<Object, Method, EventListener> factory = listenerFactories.get(annotation.annotationType());
                        EventListener listener = factory.apply(subscriber, method);
                        listeners.compute(eventType, (key, currentList) -> currentList == null ?
                                List.of(listener) :
                                Stream.concat(currentList.stream(), Stream.of(listener))
                                        .sorted(Comparator.comparingInt(EventListener::getPriority).reversed())
                                        .toList());
                        subscriptions.computeIfAbsent(subscriber, k -> new HashSet<>()).add(eventType);
                    }
                });
    }

    public void unsubscribe(Object subscriber) {
        getListeningMethods(subscriber.getClass()).forEach(method -> {
            Class<?> eventType = method.getParameterTypes()[0];
            listeners.computeIfPresent(eventType, (k, v) -> v.stream()
                    .filter(listener -> !listener.equals(subscriber))
                    .collect(Collectors.toList()));
        });
        subscriptions.remove(subscriber);
    }

    public boolean isSubscribed(Object subscriber) {
        return subscriptions.containsKey(subscriber);
    }

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<Class<? extends Annotation>, BiFunction<Object, Method, EventListener>> factories = new HashMap<>();

        public Builder() {
            factories.put(Listener.class, (instance, method) ->
                    new EventListenerImpl(instance, method, method.getAnnotation(Listener.class).priority().getVal()));
        }

        public Builder registerListenerFactory(Class<? extends Annotation> annotationType,
                                               BiFunction<Object, Method, EventListener> factory) {
            factories.put(annotationType, factory);
            return this;
        }

        public EventBus build() {
            return new EventBus(factories);
        }
    }

}
