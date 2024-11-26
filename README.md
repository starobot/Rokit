# ROKIT

[![CodeFactor](https://www.codefactor.io/repository/github/starobot/rokit/badge)](https://www.codefactor.io/repository/github/starobot/rokit)

### A fast, efficient and flexible event system for java 21.
Here my goal was to rework [StaroEventSystem](https://github.com/starobot/StaroEventSystem) entirely, make it more flexible, declarative, without the loss of performance at the end.
As of now, I believe, Rokit is a great replacement for [StaroEventSystem](https://github.com/starobot/StaroEventSystem), since java 21 improved streams performance and introduced new tools, 
which are suitable to write fast, efficient and easy to understand declarative code.

### How to use it:
First, you're gonna need to make an instance of the eventBus.
```java
EventBus eventBus = EventBus.builder().build();
```
For the custom listener annotation and listener factory you can use this builder tool.
```java
BiFunction<Object, Method, EventListener> customFactory = (instance, method) ->
                new CustomListener(instance, method, method.getAnnotation(CustomAnnotation.class).priority().getVal());

EventBus eventBus = EventBus.builder()
                .registerListenerFactory(CustomAnnotation.class, customFactory)
                .build();
```

Now, to receive an event, we need to subscribe the lister class, so the methods annotated with @Listener start receiving events.
```java
eventBus.subscribe(new EventReceivingClass());
```
To remove the listener, there's this method. The class containing listener methods will no longer get executed upon dispatching an event.
```java
eventBus.unsubscribe(new EventReceivingClass());
```

To dispatch an event, you can use method "post"
```java
eventBus.post(new Event());
```
