package bot.staro.rokit;

public class Benchmark {
    static final int WARMUP_ITERATIONS = 5;
    static final int BENCHMARK_ITERATIONS = 10;
    static final int EVENTS_PER_TEST = 1_000_000;

    public static void main(String[] args) {
        EventBus eventBus = EventBusBuilder.builder()
                .wrapSingle(WrappedEvent.class, WrappedEvent::getWrap)
                .build();

        DangListener listener = new DangListener();
        eventBus.subscribe(listener);
        eventBus.post(new BenchmarkEvent());
        eventBus.post(new WrappedEvent<>("it's a wrap"));
        eventBus.unsubscribe(listener);

        var simpleListener = new BenchmarkListener();
        var benchEvent = new BenchmarkEvent();

        runWarmup(eventBus, simpleListener, benchEvent);

        // Benchmark 1: Simple Events
        var simpleResults = new long[BENCHMARK_ITERATIONS];
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            eventBus.subscribe(simpleListener);
            simpleResults[i] = measureSimpleEvents(eventBus, benchEvent);
            eventBus.unsubscribe(simpleListener);
        }

        printResults("Simple Events", simpleResults);

        for (int i = 0; i < 200; i++) {
            eventBus.subscribe(new BenchmarkListener());
        }

        long nanos = System.nanoTime();
        eventBus.post(new BenchmarkEvent());
        long delta = System.nanoTime() - nanos;
        System.out.println("Posting one event for 200 subscribers took: " + delta);
    }

    static void runWarmup(EventBus eventRegistry, BenchmarkListener simpleListener, BenchmarkEvent benchEvent) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            eventRegistry.subscribe(simpleListener);
            measureSimpleEvents(eventRegistry, benchEvent);
            eventRegistry.unsubscribe(simpleListener);
        }
    }

    static long measureSimpleEvents(EventBus bus, BenchmarkEvent event) {
        long start = System.nanoTime();
        for (int i = 0; i < EVENTS_PER_TEST; i++) {
            bus.post(event);
        }
        return System.nanoTime() - start;
    }

    static void printResults(String testName, long[] results) {
        double avgMs = calculateAverage(results) / 1_000_000.0;
        double stdDev = calculateStdDev(results) / 1_000_000.0;
        double opsPerSec = EVENTS_PER_TEST / (avgMs / 1000);

        System.out.printf("\n%s:\n", testName);
        System.out.printf("  Average time: %.2f ms\n", avgMs);
        System.out.printf("  Std Dev: %.2f ms\n", stdDev);
        System.out.printf("  Operations/sec: %.2f\n", opsPerSec);
    }

    static double calculateAverage(long[] results) {
        double sum = 0;
        for (long result : results) {
            sum += result;
        }

        return sum / results.length;
    }

    static double calculateStdDev(long[] results) {
        double avg = calculateAverage(results);
        double sumSquares = 0;
        for (long result : results) {
            sumSquares += Math.pow(result - avg, 2);
        }

        return Math.sqrt(sumSquares / results.length);
    }

    public static class BenchmarkListener {
        @Listener
        public void onEvent(BenchmarkEvent e) {}
    }

    public static final class BenchmarkEvent {}

    public static final class WrappedEvent<W> {
        private final W wrap;

        WrappedEvent(W wrap) {
            this.wrap = wrap;
        }

        public W getWrap() {
            return wrap;
        }

    }

    public static class DangListener {
        @Listener
        public void onEvent(BenchmarkEvent ignored) {
            System.out.println("Dang");
        }

        @Listener
        public void onEvent(WrappedEvent<String> event, String wrap) {
            System.out.println(wrap);
        }
    }

}