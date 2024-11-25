import bot.staro.rokit.EventBus;
import bot.staro.rokit.Listener;
import bot.staro.rokit.impl.FunctionalEventBus;
import bot.staro.rokit.impl.ImperativeEventBus;

public class Test {
    public static void main(String[] args) {
        EventBus funcBus = new FunctionalEventBus();
        funcBus.subscribe(new TestSubscriber());
        var timer = System.currentTimeMillis();
        for (int i = 0; i <= 1000000; i++) {
            funcBus.post(new Event());
        }

        System.out.println(System.currentTimeMillis() - timer);

        EventBus imperBus = new ImperativeEventBus();
        imperBus.subscribe(new TestSubscriber());
        var t = System.currentTimeMillis();
        for (int i = 0; i <= 1000000; i++) {
            imperBus.post(new Event());
        }

        System.out.println(System.currentTimeMillis() - t);

    }

    public static class TestSubscriber{
        @Listener
        public void onEvent(Event event) {
        }

    }

    public static final class Event {}

}
