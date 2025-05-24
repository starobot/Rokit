package bot.staro.rokit.rokitbus;

import bot.staro.rokit.Event;
import bot.staro.rokit.Listener;
import bot.staro.rokit.SingletonEvent;

public final class RokitListener {
    @Listener
    public void onEvent(Event event) {
    }

    @Listener(priority = Integer.MAX_VALUE)
    public void onEvent(SingletonEvent ignored) {
        System.out.println("Priority max");
    }

    @Listener(priority = Integer.MIN_VALUE)
    public void onDifferentEvent(SingletonEvent ignored) {
        System.out.println("Priority min");
    }


}
