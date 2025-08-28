package bot.staro.rokit.rokitbus;

import bot.staro.rokit.Listener;
import bot.staro.rokit.SingletonEvent;

public final class AdditionalListener {
    @Listener
    public void onEvent(SingletonEvent ignored) {
        System.out.println("priority 0");
    }

}
