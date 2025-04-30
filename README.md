# ROKIT

[![CodeFactor](https://www.codefactor.io/repository/github/starobot/rokit/badge)](https://www.codefactor.io/repository/github/starobot/rokit)

A blazing-fast, zero-reflection, compile-time-generated event bus for Java 21.

#WARNING
##This is still in beta and is currently being tested in another project. 

## 🚀 Quick Start
**Create your bus**  
```java
 EventBus bus = EventBusBuilder.builder()
       .build();
```
**Define your listener**
```java
public class MyListener {
  @Listener
  public void onEvent(MyEvent e) {
    System.out.println("Received: " + e);
  }
}
```
**Subscribe/unsubscribe
```java
var listener = new MyListener();
bus.subscribe(listener);

bus.post(new MyEvent(/* … */));

bus.unsubscribe(listener);
```

🎁 Wrapped (Multi-arg) Events

If your event carries extra data you want injected as method arguments, register a wrapper:

```java
public class StringEvent {
  private final String payload;
  public StringEvent(String payload) { this.payload = payload; }
  public String getPayload()       { return payload;       }
}

EventBus bus = EventBusBuilder.builder()
    .wrapSingle(StringEvent.class, StringEvent::getPayload)
    .build();

public class PayloadListener {
  @Listener
  public void onString(StringEvent e, String payload) {
    System.out.println("Wrapped payload: " + payload);
  }
}
```

//TODO:
A guide on custom annotations

