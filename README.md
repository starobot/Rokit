# ROKIT — Compile-time EventBus for Java 21

[![CodeFactor](https://www.codefactor.io/repository/github/starobot/rokit/badge)](https://www.codefactor.io/repository/github/starobot/rokit)

*A blazing-fast, zero-reflection, compile-time generated event bus.*

> [!CAUTION]
> Beta – the API is mostly stable, but minor breaking changes are still possible.

---

## ✨ Features
* **No reflection in the hot path** – listener dispatch is just an array lookup.
* **Compile-time registry** – an annotation-processor pre-sorts listeners and assigns numeric event-IDs.
* **Pluggable wrappers** – unwrap events to extra method parameters without paying a runtime cost.
* **Extensible annotations** – create your own custom annotations with custom functionality by supplying a handler class.
* **Java 21**, no external dependencies (aside from `javax.annotation` for the processor).

---

## ⚙️ Installation

<details>
<summary>Gradle (Groovy DSL)</summary>

```groovy
repositories {
    mavenCentral()
    maven { url "https://jitpack.io" }
}

dependencies {
    // for the newest version - check releases.
    implementation "com.github.starobot.Rokit:rokit-api:version"
    implementation "com.github.starobot.Rokit:rokit-core:version"

    // If you use loom or specifically if you make a fabric minecraft mod - use "clientAnnotationProcessor" instead of regular "annotationProcessor"
    annotationProcessor "com.github.starobot.Rokit:rokit-processor:version"
}
```
</details>

## 🚀 Quick Start

### 1. Define an event
```java
public record ChatMessage(String text) { }
```

### 2. Create a listener
```java
public class ChatLogger {
    // Default priority is 0.
    // The method must be public, otherwise the generated listener registry, won't be able to access it.
    @Listener
    public void onChat(ChatMessage e) {
        System.out.println("Message: " + e.text());
    }
}
```

### 3. Build a bus and post events
```java
EventBus bus = RokitEventBus.builder()
        .build();

ChatLogger logger = new ChatLogger();

bus.subscribe(logger); // register
bus.post(new ChatMessage("Hello")); // dispatch
bus.unsubscribe(logger); // unregister
```

## 🎁 Wrapped (multi-arg) events
Sometimes you want to inject extra data from the event directly into the
listener method without writing boilerplate extractors.

```java
public record MoveEvent(Vector vec) {}

public record PacketHolderEvent(Packet packet, Packet otherPacket) {}

EventBus bus = RokitEventBus.builder()
        .wrap(MoveEvent.class, MoveEvent::vec) // unwrap 'vec' object
        .wrap(PacketHolderEvent.class, event -> new Object[]{event.packet(), event.otherPacket()}) // unwrap multiple objects from the same event
        .build();

public class MotionTracker {
    @Listener
    public void onMove(MoveEvent e, Vector vec) {
        System.out.printf("Moved to %.2f, %.2f%n", vec.x, vec.y);
    }

    @Listener
    public void onPacket(PacketHolderEvent event, Packet packet, Packet otherPacket) {
        // Do something.
    }
}
```

- The first parameter is always the **event itself**
- Additional parameters are filled by the **wrapper chain** you registered
in the builder.
- The processor inlines the wrapper call; no reflection, no var-args boxing.

## 🏷️ Custom listener annotations
Need extra safety checks or async dispatch? Create your own annotation in
two steps.

### 1. Declare the annotation
```java
@ListenerAnnotation(handler = SafeListenerHandler.class)
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface SafeListener {
    int priority() default 0;
}
```

### 2. Implement the handler
```java
public class SafeListenerHandler implements AnnotationHandler {
    @Override
    public <E> EventConsumer<E> createConsumer(ListenerRegistry bus, Object listenerInstance, Method method, int priority, Class<E> eventType) {
        return new EventConsumer<>() {
            @Override
            public void accept(E event) {
                // Any additional functionality here before invoking the the method.

                try {
                    method.invoke(listenerInstance, event);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }

            @Override public Object getInstance() { return listenerInstance; }
            @Override public int getPriority() { return priority; }
            @Override public Class<E> getEventType() { return eventType; }
        };
    }
}
```

The processor will detect @SafeListener, delegate consumer creation to
SafeListenerHandler, and sort it into the registry exactly like a
built-in @Listener.
