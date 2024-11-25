import bot.staro.rokit.Listener;
import bot.staro.rokit.EventBus;

public class Test {
    public static void main(String[] args) {

        // Functional event bus benchmark
        EventBus funcBus = EventBus.builder().build();
        funcBus.subscribe(new TestSubscriber());
        long timer = System.currentTimeMillis();

        for (int i = 0; i <= 1000000; i++) {
            funcBus.post(new Event());
        }

        System.out.println(System.currentTimeMillis() - timer);

    }

    public static class TestSubscriber{
        @Listener
        public void onEvent(Event event) {
        }

    }

    public static final class Event {}

}
