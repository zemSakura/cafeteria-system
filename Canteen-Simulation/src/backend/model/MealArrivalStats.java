package backend.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-period population and visualization result.
 */
public class MealArrivalStats {
    private final MealPeriod mealPeriod;
    private final int population;
    private final int backgroundPopulation;
    private final int peakPopulation;
    private final List<ArrivalPeak> peaks;
    private final List<ArrivalDistributionPoint> distributionPoints;

    public MealArrivalStats(MealPeriod mealPeriod,
                            int population,
                            int backgroundPopulation,
                            int peakPopulation,
                            List<ArrivalPeak> peaks,
                            List<ArrivalDistributionPoint> distributionPoints) {
        this.mealPeriod = mealPeriod;
        this.population = population;
        this.backgroundPopulation = backgroundPopulation;
        this.peakPopulation = peakPopulation;
        this.peaks = new ArrayList<>(peaks);
        this.distributionPoints = new ArrayList<>(distributionPoints);
    }

    public MealPeriod getMealPeriod() {
        return mealPeriod;
    }

    public int getPopulation() {
        return population;
    }

    public int getBackgroundPopulation() {
        return backgroundPopulation;
    }

    public int getPeakPopulation() {
        return peakPopulation;
    }

    public List<ArrivalPeak> getPeaks() {
        return Collections.unmodifiableList(peaks);
    }

    public List<ArrivalDistributionPoint> getDistributionPoints() {
        return Collections.unmodifiableList(distributionPoints);
    }
}
