package bot.staro.rokit;

import java.util.List;

public interface SubscriberObject {
    List<EventListener<?>> getListeners();

}
