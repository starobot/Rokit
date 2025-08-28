package bot.staro.rokit.rokitbus;

import bot.staro.rokit.*;
import bot.staro.rokit.events.*;

import java.lang.annotation.Annotation;

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

    @Listener
    public void onString(WrappedEvent<String> event) {
        System.out.println(event.getObject());
    }

    @Listener
    public void onEvent(AEvent event) {
    }

    @Listener
    public void onEvent(BEvent event) {
    }

    @Listener
    public void onEvent(CEvent event) {
    }

    @Listener
    public void onEvent(DEvent event) {
    }

    @Listener
    public void onEvent(EEvent event) {
    }

    @Listener
    public void onEvent(FEvent event) {
    }

    @Listener
    public void onEvent(GEvent event) {
    }

    @Listener
    public void onEvent(HEvent event) {
    }

    @Listener
    public void onEvent(IEvent event) {
    }

    @Listener
    public void onEvent(Jevent event) {
    }

    @Listener
    public void onEvent(KEvent event) {
    }

    @Listener
    public void onEvent(LEvent event) {
    }

    @Listener
    public void onEvent(MEvent event) {
    }

    @Listener
    public void onEvent(NEvent event) {
    }

}
