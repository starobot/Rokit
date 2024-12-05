import bot.staro.rokit.EventBus;
import bot.staro.rokit.annotation.Listener;

public class Test {
    public static void main(String[] args) {
        EventBus eventBus = EventBus.builder()
                .wrapSingle(Event.class, Event::getSomething)
                .build();
        TestSubscriber testSubscriber = new TestSubscriber();
        eventBus.subscribe(testSubscriber);
        eventBus.post(new Event<>("kek"));
        eventBus.unsubscribe(testSubscriber);
        eventBus.post(new Event<>("kek"));

        // Functional event bus benchmark
        /*eventBus.subscribe(new BenchmarkListener());
        long timer = System.currentTimeMillis();

        for (int i = 0; i <= 1000000; i++) {
            eventBus.post(new BenchmarkEvent());
        }

        System.out.println(System.currentTimeMillis() - timer);*/
    }

    public static class BenchmarkListener {
        @Listener
        private void onEvent(BenchmarkEvent e) {}
    }

    public static class TestSubscriber {
        @Listener
        public void onEvent(Event<?> event, String string) {
            System.out.println(string);
        }
    }

    public static final class BenchmarkEvent {}

    public static final class Event<String> {
        private final String something;

        public Event(String something) {
            this.something = something;
        }

        public String getSomething() {
            return something;
        }
    }

}
