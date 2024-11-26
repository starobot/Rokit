package bot.staro.rokit;

import bot.staro.rokit.annotation.Listener;
import bot.staro.rokit.annotation.TypeHandler;
import bot.staro.rokit.impl.BiEventConsumer;
import bot.staro.rokit.impl.EventConsumerImpl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The central bus for dispatching (posting) events and managing subscribers (listeners).
 * Allows subscription of objects that include methods annotated with {@link Listener} or any other
 *  registered annotation using {@code registerListenerFactory(Class<? extends Annotation> annotationType,
 *                                                BiFunction<Object, Method, EventListener> factory)}.
 */
public class EventBus {
    private final Map<Class<?>, List<EventConsumer>> listeners = new ConcurrentHashMap<>();
    private final Map<Object, Set<Class<?>>> subscriptions = new ConcurrentHashMap<>();
    private final Map<Class<? extends Annotation>, BiFunction<Object, Method, EventConsumer>> listenerFactories = new HashMap<>();

    private EventBus(Map<Class<? extends Annotation>, BiFunction<Object, Method, EventConsumer>> factories) {
        this.listenerFactories.putAll(factories);
    }

    /**
     * Dispatches (posts) an event to the subscribers.
     *
     * @param event is the object to dispatch.
     */
    public void post(Object event) {
        listeners.getOrDefault(event.getClass(), Collections.emptyList())
                .forEach(listener -> listener.invoke(event));
    }

    /**
     * Subscribes an object. Any methods annotated with {@link Listener}
     *     and fitting the format of {@code void methodName(Event event)} will receive the dispatched event object.
     *
     * @param subscriber is an object containing listener methods.
     */
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
                        BiFunction<Object, Method, EventConsumer> factory = listenerFactories.get(annotation.annotationType());
                        EventConsumer listener = factory.apply(subscriber, method);
                        listeners.compute(eventType, (key, currentList) -> currentList == null ?
                                List.of(listener) :
                                Stream.concat(currentList.stream(), Stream.of(listener))
                                        .sorted(Comparator.comparingInt(EventConsumer::getPriority).reversed())
                                        .toList());
                        subscriptions.computeIfAbsent(subscriber, k -> new HashSet<>()).add(eventType);
                    }
                });
    }

    /**
     * Unsubscribes an object. The unsubscribed object will no longer receive dispatched events.
     *
     * @param subscriber is an object containing listener methods.
     */
    public void unsubscribe(Object subscriber) {
        getListeningMethods(subscriber.getClass()).forEach(method -> {
            Class<?> eventType = method.getParameterTypes()[0];
            listeners.computeIfPresent(eventType, (k, list) -> list.stream()
                    .filter(listener -> !listener.getInstance().equals(subscriber))
                    .collect(Collectors.toList()));
        });

        subscriptions.remove(subscriber);
    }

    /**
     * Checks if an object is currently subscribed to the EventBus.
     *
     * @param subscriber the object to check.
     * @return {@code true} if the object is subscribed, {@code false} otherwise.
     */
    public boolean isSubscribed(Object subscriber) {
        return subscriptions.containsKey(subscriber);
    }

    private List<Method> getListeningMethods(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(method -> /*method.getParameterCount() == 1 &&*/
                        Arrays.stream(method.getAnnotations())
                                .anyMatch(annotation -> listenerFactories.containsKey(annotation.annotationType())))
                .collect(Collectors.toList());
    }

    /**
     * Creates a builder for constructing an EventBus with custom factories.
     *
     * @return a new {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder for creating an {@link EventBus}.
     * Supports adding custom listener factories before building the EventBus.
     */
    public static class Builder {
        private final Map<Class<? extends Annotation>, BiFunction<Object, Method, EventConsumer>> factories = new HashMap<>();

        public Builder() {
            factories.put(Listener.class, (instance, method) -> {
                if (method.getAnnotation(TypeHandler.class) != null) {
                    return new BiEventConsumer(instance, method, method.getAnnotation(Listener.class).priority().getVal());
                }

                return new EventConsumerImpl(instance, method, method.getAnnotation(Listener.class).priority().getVal());
            });
        }

        /**
         * Registers a custom listener factory for a specific annotation type.
         * An example of such registry can be found inside {@link Builder} constructor.
         *
         * @param annotationType the annotation class used to mark methods.
         * @param factory        the factory function to create listeners.
         */
        public Builder registerListenerFactory(Class<? extends Annotation> annotationType,
                                               BiFunction<Object, Method, EventConsumer> factory) {
            factories.put(annotationType, factory);
            return this;
        }

        /**
         * Builds a new EventBus object.
         *
         * @return new EventBus instance.
         */
        public EventBus build() {
            return new EventBus(factories);
        }
    }

}
