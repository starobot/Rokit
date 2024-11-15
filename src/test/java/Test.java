import bot.staro.rokit.EventBus;
import bot.staro.rokit.EventListener;
import bot.staro.rokit.impl.EventBusImpl;
import bot.staro.rokit.impl.Listener;
import bot.staro.rokit.impl.Subscriber;

public class Test {
    public static void main(String[] args) {
        EventBus eventBus = new EventBusImpl();
        eventBus.subscribe(new TestSubscriber());
        var timer = System.currentTimeMillis();
        for (int i = 0; i <= 1000000; i++) eventBus.post(new Event());
        System.out.println(System.currentTimeMillis() - timer);
    }

    public static class TestSubscriber extends Subscriber {
        public TestSubscriber() {
            EventListener<Event> listener = Listener.<Event>builder()
                    .withType(Event.class)
                    .withConsumer(this::onEvent)
                    .build();
            addListener(listener);
        }

        public void onEvent(Event event) {
        }

    }

    public static final class Event {}

}
