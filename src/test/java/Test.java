import bot.staro.rokit.Listener;
import bot.staro.rokit.EventBus;

public class Test {
    public static void main(String[] args) {
        EventBus eventBus = EventBus.builder().build();
        eventBus.subscribe(new TestSubscriber());
        //eventBus.post(new Event());

        // Functional event bus benchmark
        long timer = System.currentTimeMillis();

        for (int i = 0; i <= 1000000; i++) {
            eventBus.post(new Event());
        }

        System.out.println(System.currentTimeMillis() - timer);
    }

    public static class TestSubscriber{
        @SuppressWarnings("unused")
        @Listener
        public void onEvent(Event event) {
            //System.out.println("Received");
        }

    }

    public static final class Event {}

}
