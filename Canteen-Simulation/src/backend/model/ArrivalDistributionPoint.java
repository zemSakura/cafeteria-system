package backend.model;

/**
 * Discrete per-minute intensity point prepared for frontend charting.
 */
public class ArrivalDistributionPoint {
    private final MealPeriod mealPeriod;
    private final int minuteOfDay;
    private final int minuteFromSimulationStart;
    private final double baseIntensity;
    private final double peakIntensity;
    private final double totalIntensity;

    public ArrivalDistributionPoint(MealPeriod mealPeriod,
                                    int minuteOfDay,
                                    int minuteFromSimulationStart,
                                    double baseIntensity,
                                    double peakIntensity,
                                    double totalIntensity) {
        this.mealPeriod = mealPeriod;
        this.minuteOfDay = minuteOfDay;
        this.minuteFromSimulationStart = minuteFromSimulationStart;
        this.baseIntensity = baseIntensity;
        this.peakIntensity = peakIntensity;
        this.totalIntensity = totalIntensity;
    }

    public MealPeriod getMealPeriod() {
        return mealPeriod;
    }

    public int getMinuteOfDay() {
        return minuteOfDay;
    }

    public int getMinuteFromSimulationStart() {
        return minuteFromSimulationStart;
    }

    public double getBaseIntensity() {
        return baseIntensity;
    }

    public double getPeakIntensity() {
        return peakIntensity;
    }

    public double getTotalIntensity() {
        return totalIntensity;
    }
}
