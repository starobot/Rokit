package bot.staro.rokit;

@FunctionalInterface
public interface EventWrapper {
    Object handle(Object event);

}
