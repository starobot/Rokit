package bot.staro.rokit.events;

public class CancellableEvent {
    private boolean cancelled;

    public CancellableEvent() {
        cancelled = false;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

}
