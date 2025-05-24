package bot.staro.rokit;

@SuppressWarnings("InstantiationOfUtilityClass")
public final class SingletonEvent {
    private static final SingletonEvent INSTANCE = new SingletonEvent();

    private SingletonEvent() {
    }

    public static SingletonEvent getInstance() {
        return INSTANCE;
    }

}
