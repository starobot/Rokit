# ROKIT

[![CodeFactor](https://www.codefactor.io/repository/github/starobot/rokit/badge)](https://www.codefactor.io/repository/github/starobot/rokit)

### A fast, efficient and flexible event system for java 21.
Here my goal was to rework [StaroEventSystem](https://github.com/starobot/StaroEventSystem) entirely, make it more flexible, declarative, without the loss of performance at the end.
As of now, I believe, Rokit is a great replacement for [StaroEventSystem](https://github.com/starobot/StaroEventSystem), since java 21 improved streams performance and introduced new tools, 
which are suitable to write fast, efficient and easy to understand declarative code.

Dependency:
gradle:
```
mavenCentral()
maven { url 'https://jitpack.io' }
```
```
implementation 'com.github.starobot:Rokit:1.1'
```
maven:
```
<repositories>
	<repository>
		<id>jitpack.io</id>
		 <url>https://jitpack.io</url>
	</repository>
</repositories>
```
```
	<dependency>
	    <groupId>com.github.starobot</groupId>
	    <artifactId>Rokit</artifactId>
	    <version>1.1</version>
	</dependency>
```

### How to use it:
First, you're gonna need to make an instance of the eventBus.
```java
EventBus eventBus = EventBus.builder()
                .build();
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
The eventBus also supports dispatching the event data type directly. (Shoutout to [cattyn](https://github.com/cattyngmd))
For posting such an event, you must make a wrapper for the event class and specify the field you are passing.
For example, if we have an event with a String:
```java
public class StringEvent {
    private final String name;

    public StringEvent(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }

}
```
Now, to successfully dispatch it we would only need to specify a wrapper for the event:
```java
EventBus eventBus = EventBus.builder()
                .wrapSingle(StringEvent.class, StringEvent::getName)
                .build();
```
To get the data directly, you can make make a listener method with two arguments now:
```java
@Listener
public void onEvent(StringEvent<?> event, String name) {
     //if (name ....
}
```
