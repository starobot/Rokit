package bot.staro.rokit.impl;

import bot.staro.rokit.EventListener;
import bot.staro.rokit.SubscriberObject;

import java.util.ArrayList;
import java.util.List;

public class Subscriber implements SubscriberObject {
    private final List<EventListener<?>> eventListeners = new ArrayList<>();

    @Override
    public List<EventListener<?>> getListeners() {
        return eventListeners;
    }

    public <E> void addListener(EventListener<E> eventListener) {
        eventListeners.add(eventListener);
    }

    public <E> Subscriber addListener(Listener.Builder<E> builder) {
        eventListeners.add(builder.build());
        return this;
    }

}
