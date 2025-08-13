package bot.staro.rokit;

import bot.staro.rokit.rokitbus.AdditionalLIstener;
import bot.staro.rokit.rokitbus.RokitListener;

public final class Benchmark {
    public static void main(String[] args) {
        EventBus eventBus = RokitEventBus.builder()
                .wrap(WrappedEvent.class, WrappedEvent::getObject)
                .build();
        var rokitListener = new RokitListener();
        var additionalListener = new AdditionalLIstener();
        var event = new Event();

        // JVM warmup
        BenchmarkUtil.runWarmup(() -> eventBus.subscribe(rokitListener), () -> eventBus.post(event), () -> eventBus.unsubscribe(rokitListener));

        // 1 Million events

        // Rokit
        eventBus.subscribe(rokitListener);
        eventBus.subscribe(additionalListener);
        eventBus.post(SingletonEvent.getInstance());
        eventBus.post(new WrappedEvent<>("Message from wrapped event received!"));

        var rokitResults = new long[BenchmarkUtil.BENCHMARK_ITERATIONS];
        for (int i = 0; i < BenchmarkUtil.BENCHMARK_ITERATIONS; i++) {
            rokitResults[i] = BenchmarkUtil.measurePureDispatch(eventBus::post, event);
        }

        printResults("1 Million events - 1 subscriber", rokitResults);
    }

    static void printResults(String testName, long[] results) {
        double avgMs = MeasuringUtil.calculateAverage(results) / 1_000_000.0;
        double stdDev = MeasuringUtil.calculateStdDev(results) / 1_000_000.0;
        double opsPerSec = BenchmarkUtil.EVENTS_PER_TEST / (avgMs / 1000);
        System.out.printf("\n%s:\n", testName);
        System.out.printf("  Average time: %.2f ms\n", avgMs);
        System.out.printf("  Std Dev: %.2f ms\n", stdDev);
        System.out.printf("  Operations/sec: %.2f\n", opsPerSec);
    }

}