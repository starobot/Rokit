package bot.staro.rokit;

import bot.staro.rokit.rokitbus.RokitListener;

public final class Benchmark {
    public static void main(String[] args) {
        EventBus eventBus = RokitEventBus.builder().build();
        var rokitListener = new RokitListener();
        var event = new Event();

        // JVM warmup
        BenchmarkUtil.runWarmup(() -> eventBus.subscribe(rokitListener), () -> eventBus.post(event), () -> eventBus.unsubscribe(rokitListener));

        // 1 Million events

        // Rokit
        eventBus.subscribe(rokitListener);
        var rokitResults = new long[BenchmarkUtil.BENCHMARK_ITERATIONS];
        for (int i = 0; i < BenchmarkUtil.BENCHMARK_ITERATIONS; i++) {
            rokitResults[i] = BenchmarkUtil.measurePureDispatch(eventBus::post, event);
        }

        eventBus.unsubscribe(rokitListener);
        printResults("1 Million events - 1 subscriber", rokitResults);

        for (int i = 0; i < 1000; i++) {
            eventBus.subscribe(new RokitListener());
        }

        long l = System.nanoTime();
        eventBus.post(new Event());
        double result = (System.nanoTime() - l) / 1_000_000F;
        System.out.println("1000 subscribers - 1 event " + result);
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