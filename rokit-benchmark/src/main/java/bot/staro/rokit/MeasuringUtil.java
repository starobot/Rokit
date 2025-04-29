package bot.staro.rokit;

public final class MeasuringUtil {
    private MeasuringUtil() {
    }

    public static double calculateAverage(long[] results) {
        double sum = 0;
        for (long result : results) {
            sum += result;
        }

        return sum / results.length;
    }

    public static double calculateStdDev(long[] results) {
        double avg = calculateAverage(results);
        double sumSquares = 0;
        for (long result : results) {
            sumSquares += Math.pow(result - avg, 2);
        }

        return Math.sqrt(sumSquares / results.length);
    }

}
