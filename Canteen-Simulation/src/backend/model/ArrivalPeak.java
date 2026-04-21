package backend.model;

/**
 * Gaussian peak descriptor for visualization and arrival generation.
 *
 * Formula:
 * lambda(t) = base + sum(amplitude * exp(-(t - mean)^2 / (2 * sigma^2)))
 */
public class ArrivalPeak {
    private final String name;
    private final MealPeriod mealPeriod;
    private final int meanMinuteOfDay;
    private final double sigmaMinutes;
    private final double amplitudePerMinute;
    private final String source;

    public ArrivalPeak(String name,
                       MealPeriod mealPeriod,
                       int meanMinuteOfDay,
                       double sigmaMinutes,
                       double amplitudePerMinute,
                       String source) {
        this.name = name;
        this.mealPeriod = mealPeriod;
        this.meanMinuteOfDay = meanMinuteOfDay;
        this.sigmaMinutes = sigmaMinutes;
        this.amplitudePerMinute = amplitudePerMinute;
        this.source = source;
    }

    public String getName() {
        return name;
    }

    public MealPeriod getMealPeriod() {
        return mealPeriod;
    }

    public int getMeanMinuteOfDay() {
        return meanMinuteOfDay;
    }

    public double getSigmaMinutes() {
        return sigmaMinutes;
    }

    public double getAmplitudePerMinute() {
        return amplitudePerMinute;
    }

    public String getSource() {
        return source;
    }
}
