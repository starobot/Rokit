import bot.staro.rokit.EventWrapper;
import bot.staro.rokit.annotation.Listener;
import bot.staro.rokit.EventBus;
import bot.staro.rokit.annotation.TypeHandler;

public class Test {
    public static void main(String[] args) {
        EventBus eventBus = EventBus.builder().build();
        eventBus.subscribe(new TestSubscriber());
        eventBus.post(new Event<>("1"));

        // Functional event bus benchmark
        eventBus.subscribe(new BenchmarkListener());
        /*long timer = System.currentTimeMillis();

        for (int i = 0; i <= 1000000; i++) {
            eventBus.post(new BenchmarkEvent());
        }

        System.out.println(System.currentTimeMillis() - timer);*/
    }

    public static class BenchmarkListener {
        @Listener
        private void onEvent(BenchmarkEvent e) {}
    }

    public static final class BenchmarkEvent {}

    public static class TestSubscriber {

        @TypeHandler(EventWrapperImpl.class)
        @Listener
        public void onEvent(Event<?> event, String string) {
            System.out.println("1");
            //System.out.println("Received");
        }
    }

    public static final class Event<T> {
        public T something;

        public Event(T something) {
            this.something = something;
        }
    }

    public static final class EventWrapperImpl implements EventWrapper {
        @Override
        public Object handle(Object event) {
            if (event instanceof Event<?> e) {
                return e.something;
            }

            return null;
        }
    }

}
