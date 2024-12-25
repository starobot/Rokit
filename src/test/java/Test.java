import bot.staro.rokit.EventBus;
import bot.staro.rokit.annotation.Listener;

public class Test {
    private static final int WARMUP_ITERATIONS = 5;
    private static final int BENCHMARK_ITERATIONS = 10;
    private static final int EVENTS_PER_TEST = 1_000_000;

    public static void main(String[] args) {
        EventBus eventBus = EventBus.builder()
                .wrapSingle(Event.class, Event::getSomething)
                .build();

        // Test wrapped event
        WrappedListener testSubscriber = new WrappedListener();
        eventBus.subscribe(testSubscriber);
        eventBus.post(new Event<>("kek"));
        eventBus.unsubscribe(testSubscriber);
        eventBus.post(new Event<>("kek"));

        // Test benchmark event
        var simpleListener = new BenchmarkListener();
        var benchEvent = new BenchmarkEvent();

        // Makes jvm less volatile in results.
        runWarmup(eventBus, simpleListener, benchEvent);

        // Benchmark 1: Simple Events
        var simpleResults = new long[BENCHMARK_ITERATIONS];
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            eventBus.subscribe(simpleListener);
            simpleResults[i] = measureSimpleEvents(eventBus, benchEvent);
            eventBus.unsubscribe(simpleListener);
        }

        //TODO: make a wrapped listener benchmark

        printResults("Simple Events", simpleResults);
    }

    private static void runWarmup(EventBus eventBus, BenchmarkListener simpleListener, BenchmarkEvent benchEvent) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            eventBus.subscribe(simpleListener);
            measureSimpleEvents(eventBus, benchEvent);
            eventBus.unsubscribe(simpleListener);
        }
    }

    private static long measureSimpleEvents(EventBus bus, BenchmarkEvent event) {
        long start = System.nanoTime();
        for (int i = 0; i < EVENTS_PER_TEST; i++) {
            bus.post(event);
        }
        return System.nanoTime() - start;
    }

    private static long measureWrappedEvents(EventBus bus, Event<String> event) {
        long start = System.nanoTime();
        for (int i = 0; i < EVENTS_PER_TEST; i++) {
            bus.post(event);
        }
        return System.nanoTime() - start;
    }

    private static void printResults(String testName, long[] results) {
        double avgMs = calculateAverage(results) / 1_000_000.0;
        double stdDev = calculateStdDev(results) / 1_000_000.0;
        double opsPerSec = EVENTS_PER_TEST / (avgMs / 1000);

        System.out.printf("\n%s:\n", testName);
        System.out.printf("  Average time: %.2f ms\n", avgMs);
        System.out.printf("  Std Dev: %.2f ms\n", stdDev);
        System.out.printf("  Operations/sec: %.2f\n", opsPerSec);
    }

    private static double calculateAverage(long[] results) {
        double sum = 0;
        for (long result : results) {
            sum += result;
        }

        return sum / results.length;
    }

    private static double calculateStdDev(long[] results) {
        double avg = calculateAverage(results);
        double sumSquares = 0;
        for (long result : results) {
            sumSquares += Math.pow(result - avg, 2);
        }

        return Math.sqrt(sumSquares / results.length);
    }

    public static class BenchmarkListener {
        @Listener
        private void onEvent(BenchmarkEvent e) {}
    }

    public static class WrappedListener {
        @Listener
        public void onEvent(Event<?> event, String string) {
            System.out.println(string);
        }
    }

    public static final class BenchmarkEvent {}

    public static final class Event<String> {
        private final String something;

        public Event(String something) {
            this.something = something;
        }

        public String getSomething() {
            return something;
        }
    }

}
