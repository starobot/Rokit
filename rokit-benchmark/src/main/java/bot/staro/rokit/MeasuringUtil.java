package bot.staro.rokit;

public final class MeasuringUtil {
    private MeasuringUtil() {
    }

    public static double calculateAverage(final long[] results) {
        double sum = 0;
        for (final long result : results) {
            sum += result;
        }

        return sum / results.length;
    }

    public static double calculateStdDev(final long[] results) {
        final double avg = calculateAverage(results);
        double sumSquares = 0;
        for (final long result : results) {
            sumSquares += Math.pow(result - avg, 2);
        }

        return Math.sqrt(sumSquares / results.length);
    }

}
