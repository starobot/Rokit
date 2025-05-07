package bot.staro.rokit;

import bot.staro.rokit.rokitbus.RokitListener;

public final class Benchmark {
    public static void main(String[] args) {
        EventBus rokit = RokitEventBus.builder().build();
        var rokitListener = new RokitListener();
        var event = new Event();

        // JVM warmup
        BenchmarkUtil.runWarmup(() -> rokit.subscribe(rokitListener), () -> rokit.post(event), () -> rokit.unsubscribe(rokitListener));

        // 1 Million events

        // Rokit
        rokit.subscribe(rokitListener);
        var rokitResults = new long[BenchmarkUtil.BENCHMARK_ITERATIONS];
        for (int i = 0; i < BenchmarkUtil.BENCHMARK_ITERATIONS; i++) {
            rokitResults[i] = BenchmarkUtil.measurePureDispatch(rokit::post, event);
        }

        rokit.unsubscribe(rokitListener);
        printResults("Rokit 1_000_000 events", rokitResults);
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