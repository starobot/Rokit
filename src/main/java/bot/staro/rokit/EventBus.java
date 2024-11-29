package bot.staro.rokit;

import bot.staro.rokit.annotation.Listener;
import bot.staro.rokit.function.EventWrapper;
import bot.staro.rokit.function.SingleEventWrapper;
import bot.staro.rokit.impl.MultiEventConsumer;
import bot.staro.rokit.impl.EventConsumerImpl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

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
    private final Map<Class<?>, EventWrapper<?>> eventWrappers = new HashMap<>();

    private EventBus(Map<Class<? extends Annotation>, BiFunction<Object, Method, EventConsumer>> factories,
                     Map<Class<?>, EventWrapper<?>> eventWrappers) {
        this.listenerFactories.put(Listener.class, (instance, method) -> {
            var priority = method.getAnnotation(Listener.class).priority().getVal();
            var wrapper = getWrapper(method);
            if (wrapper != null) {
                return new MultiEventConsumer(instance, method, priority, wrapper);
            }

            return new EventConsumerImpl(instance, method, priority);
        });

        this.listenerFactories.putAll(factories);

        this.eventWrappers.putAll(eventWrappers);
    }

    /**
     * Dispatches (posts) an event to the subscribers.
     *
     * @param event is the object to dispatch.
     */
    public void post(Object event) {
        List<EventConsumer> consumers = listeners.getOrDefault(event.getClass(), Collections.emptyList());
        if (consumers != null) {
            for (EventConsumer consumer : consumers) {
                consumer.invoke(event);
            }
        }
    }

    /**
     * Subscribes an object. Any methods annotated with {@link Listener}
     *     and fitting the format of {@code void methodName(Event event)} will receive the dispatched event object.
     *
     * @param subscriber is an object containing listener methods.
     */
    public void subscribe(Object subscriber) {
        List<Method> methods = getListeningMethods(subscriber.getClass());
        for (Method method : methods) {
            Annotation[] annotations = method.getAnnotations();
            Annotation targetAnnotation = null;
            for (Annotation annotation : annotations) {
                if (listenerFactories.containsKey(annotation.annotationType())) {
                    targetAnnotation = annotation;
                    break;
                }
            }

            if (targetAnnotation != null) {
                Class<?> eventType = method.getParameterTypes()[0];
                BiFunction<Object, Method, EventConsumer> factory = listenerFactories.get(targetAnnotation.annotationType());
                EventConsumer listener = factory.apply(subscriber, method);
                listeners.compute(eventType, (key, currentList) -> {
                    if (currentList == null) {
                        return List.of(listener);
                    }

                    List<EventConsumer> newList = new ArrayList<>(currentList);
                    newList.add(listener);
                    newList.sort(Comparator.comparingInt(EventConsumer::getPriority).reversed());
                    return newList;
                });

                subscriptions.computeIfAbsent(subscriber, k -> new HashSet<>()).add(eventType);
            }
        }
    }

    /**
     * Unsubscribes an object. The unsubscribed object will no longer receive dispatched events.
     *
     * @param subscriber is an object containing listener methods.
     */
    public void unsubscribe(Object subscriber) {
        List<Method> methods = getListeningMethods(subscriber.getClass());
        for (Method method : methods) {
            Class<?> eventType = method.getParameterTypes()[0];
            listeners.computeIfPresent(eventType, (k, list) -> list.stream()
                    .filter(listener -> !listener.getInstance().equals(subscriber))
                    .collect(Collectors.toList()));
        }

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
     * Returns an {@link EventWrapper} for targeting method
     *
     * @param method search target
     * @return {@link EventWrapper} instance or {@code null} if wrapper not found
     */
    private EventWrapper<?> getWrapper(Method method) {
        Parameter[] p = method.getParameters();
        if (p.length < 2) {
            return null;
        }

        Class<?> type = p[0].getType();
        return eventWrappers.get(type);
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
        private final Map<Class<?>, EventWrapper<?>> eventWrappers = new HashMap<>();

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

        public <T> Builder wrap(Class<T> k, EventWrapper<? super T> wrapper) {
            eventWrappers.put(k, wrapper);
            return this;
        }

        public <T> Builder wrapSingle(Class<T> k, SingleEventWrapper<? super T> wrapper) {
            eventWrappers.put(k, wrapper);
            return this;
        }

        /**
         * Builds a new EventBus object.
         *
         * @return new EventBus instance.
         */
        public EventBus build() {
            return new EventBus(factories, eventWrappers);
        }

    }

}
