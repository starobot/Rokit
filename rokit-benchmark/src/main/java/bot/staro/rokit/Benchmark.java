package bot.staro.rokit;

import bot.staro.rokit.guavabus.GuavaListener;
import bot.staro.rokit.rokitbus.RokitListener;
import meteordevelopment.orbit.IEventBus;

import java.lang.invoke.MethodHandles;

public final class Benchmark {
    public static void main(String[] args) {
        EventBus rokit = EventBusBuilder.builder().build();
        IEventBus orbit = new meteordevelopment.orbit.EventBus();
        orbit.registerLambdaFactory("bruh", (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));
        var guava = new com.google.common.eventbus.EventBus();

        var rokitListener = new RokitListener();
        var guavaListener = new GuavaListener();

        var event = new Event();

        // JVM warmup
        BenchmarkUtil.runWarmup(() -> rokit.subscribe(rokitListener), () -> rokit.post(event), () -> rokit.unsubscribe(rokitListener));
        //BenchmarkUtil.runWarmup(() -> orbit.subscribe(accept(event)), () -> orbit.post(event), () -> orbit.unsubscribe(this));
        BenchmarkUtil.runWarmup(() -> guava.register(guavaListener), () -> guava.post(event), () -> guava.unregister(guavaListener));

        // 1 Million events

        // Rokit
        rokit.subscribe(rokitListener);
        var rokitResults = new long[BenchmarkUtil.BENCHMARK_ITERATIONS];
        for (int i = 0; i < BenchmarkUtil.BENCHMARK_ITERATIONS; i++) {
            rokitResults[i] = BenchmarkUtil.measurePureDispatch(rokit::post, event);
        }

        rokit.unsubscribe(rokitListener);
        printResults("Rokit 1_000_000 events", rokitResults);

        // Orbit
        /*orbit.subscribe(this);
        var orbitResults = new long[BenchmarkUtil.BENCHMARK_ITERATIONS];
        for (int i = 0; i < BenchmarkUtil.BENCHMARK_ITERATIONS; i++) {
            orbitResults[i] = BenchmarkUtil.measurePureDispatch(orbit::post, event);
        }

        orbit.unsubscribe(this);
        printResults("Orbit 1_000_000 events", orbitResults);*/

        // Guava
        guava.register(guavaListener);
        var guavaResults = new long[BenchmarkUtil.BENCHMARK_ITERATIONS];
        for (int i = 0; i < BenchmarkUtil.BENCHMARK_ITERATIONS; i++) {
            guavaResults[i] = BenchmarkUtil.measurePureDispatch(guava::post, event);
        }

        guava.unregister(guavaListener);
        printResults("Orbit 1_000_000 events", guavaResults);
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