package bot.staro.rokit;

import java.util.function.Consumer;

public final class BenchmarkUtil {
    public static final int WARMUP_ITERATIONS = 69;
    public static final int BENCHMARK_ITERATIONS = 10;
    public static final int EVENTS_PER_TEST = 1_000_000;

    private BenchmarkUtil() {
    }

    public static void runWarmup(Runnable subscribe, Runnable post, Runnable unsubscribe) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            subscribe.run();
            post.run();
            unsubscribe.run();
        }
    }

    public static long measurePureDispatch(Consumer<Object> eventConsumer, Object event) {
        long start = System.nanoTime();
        for (int i = 0; i < EVENTS_PER_TEST; i++) {
            eventConsumer.accept(event);
        }

        return System.nanoTime() - start;
    }

}
