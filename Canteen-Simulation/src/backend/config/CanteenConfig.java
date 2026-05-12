package backend.config;

import backend.model.MealPeriod;
import backend.model.SimulationMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Global runtime configuration for the cafeteria simulation.
 *
 * Notes for the current simulation version:
 * 1. The frontend openDuration field is still entered in minutes.
 * 2. Backend event time is stored in seconds, so openDuration will be converted
 *    to seconds when generating arrival times.
 * 3. Window service time, patience time and dining time are all stored in seconds.
 */
public class CanteenConfig {

    private CanteenConfig() {
    }

    public static final String TIME_UNIT_DESCRIPTION = "1 simulation time unit = 1 second";

    // =========================================================
    // Default values
    // =========================================================

    public static final int DEFAULT_TOTAL_TABLES = 150;

    /**
     * Frontend input unit: minute.
     */
    public static final int DEFAULT_OPEN_DURATION = 120;

    /**
     * Unit: second. Default gap between full-day meal periods is 60 minutes.
     */
    public static final int DEFAULT_MEAL_GAP_TICKS = 60 * 60;

    public static final int DEFAULT_SNAPSHOT_INTERVAL = 5;
    public static final long DEFAULT_RANDOM_SEED = 20260324L;

    public static final int[] DEFAULT_WINDOW_DISTANCES = {10, 15, 20, 25, 30};

    /**
     * Unit: second.
     * Fast windows are about 15-25 seconds; slow windows are about 60-120 seconds.
     */
    public static final int[] DEFAULT_WINDOW_AVG_SERVE_TIME = {18, 21, 24, 19, 90};

    /**
     * Unit: second. Default dining time is around 15 minutes.
     */
    public static final double DEFAULT_DINING_TIME_MEAN = 15.0 * 60.0;
    public static final double DEFAULT_DINING_TIME_STD = 3.0 * 60.0;
    public static final int DEFAULT_MIN_DINING_TIME = 5 * 60;

    /**
     * Unit: second. Students tolerate about 15-30 minutes of queueing.
     */
    public static final int DEFAULT_PATIENCE_MIN = 15 * 60;
    public static final int DEFAULT_PATIENCE_MAX = 30 * 60;

    public static final double DEFAULT_PROB_SOLO = 0.7;
    public static final double DEFAULT_PROB_DUO = 0.2;
    public static final double DEFAULT_PROB_TEAM = 0.1;

    public static final int DEFAULT_TOTAL_POPULATION = 1000;
    public static final SimulationMode DEFAULT_SIMULATION_MODE = SimulationMode.SINGLE_PERIOD;
    public static final MealPeriod DEFAULT_MEAL_PERIOD = MealPeriod.LUNCH;

    public static final double DEFAULT_BREAKFAST_BASE_RATE = 0.22;
    public static final double DEFAULT_EARLY_CLASS_RATIO = 0.35;
    public static final double DEFAULT_BREAKFAST_SKIP_RATE = 0.35;
    public static final double DEFAULT_EARLY_CLASS_BREAKFAST_BOOST = 0.18;

    public static final double DEFAULT_LUNCH_BASE_RATE = 0.72;
    public static final double DEFAULT_TAKEOUT_RATE_AT_LUNCH = 0.12;
    public static final double[] DEFAULT_LUNCH_BATCH_WEIGHTS = {0.55, 0.45};
    public static final int[] DEFAULT_LUNCH_RELEASE_TIMES = {11 * 60 + 50, 12 * 60 + 10};
    public static final int[] DEFAULT_LUNCH_PEAK_DELAY_MEANS = {8, 10};

    public static final double DEFAULT_DINNER_BASE_RATE = 0.52;
    public static final double DEFAULT_EVENING_CLASS_RATIO = 0.25;
    public static final double DEFAULT_DINNER_SKIP_OR_OFF_CAMPUS_RATE = 0.18;
    public static final int DEFAULT_DINNER_UNIFIED_RELEASE_TIME = 17 * 60 + 40;
    public static final int DEFAULT_DINNER_PEAK_DELAY_MEAN = 12;

    public static final double DEFAULT_BACKGROUND_FLOW_RATIO = 0.18;

    // =========================================================
    // Runtime values
    // =========================================================

    public static int TOTAL_TABLES = DEFAULT_TOTAL_TABLES;
    public static int OPEN_DURATION = DEFAULT_OPEN_DURATION;
    public static int MEAL_GAP_TICKS = DEFAULT_MEAL_GAP_TICKS;
    public static int SNAPSHOT_INTERVAL = DEFAULT_SNAPSHOT_INTERVAL;
    public static long RANDOM_SEED = DEFAULT_RANDOM_SEED;

    public static int[] WINDOW_DISTANCES = DEFAULT_WINDOW_DISTANCES.clone();
    public static int[] WINDOW_AVG_SERVE_TIME = DEFAULT_WINDOW_AVG_SERVE_TIME.clone();

    public static double DINING_TIME_MEAN = DEFAULT_DINING_TIME_MEAN;
    public static double DINING_TIME_STD = DEFAULT_DINING_TIME_STD;
    public static int MIN_DINING_TIME = DEFAULT_MIN_DINING_TIME;

    public static int PATIENCE_MIN = DEFAULT_PATIENCE_MIN;
    public static int PATIENCE_MAX = DEFAULT_PATIENCE_MAX;

    public static double PROB_SOLO = DEFAULT_PROB_SOLO;
    public static double PROB_DUO = DEFAULT_PROB_DUO;
    public static double PROB_TEAM = DEFAULT_PROB_TEAM;

    public static int TOTAL_POPULATION = DEFAULT_TOTAL_POPULATION;
    public static SimulationMode SIMULATION_MODE = DEFAULT_SIMULATION_MODE;
    public static MealPeriod MEAL_PERIOD = DEFAULT_MEAL_PERIOD;

    public static double BREAKFAST_BASE_RATE = DEFAULT_BREAKFAST_BASE_RATE;
    public static double EARLY_CLASS_RATIO = DEFAULT_EARLY_CLASS_RATIO;
    public static double BREAKFAST_SKIP_RATE = DEFAULT_BREAKFAST_SKIP_RATE;
    public static double EARLY_CLASS_BREAKFAST_BOOST = DEFAULT_EARLY_CLASS_BREAKFAST_BOOST;

    public static double LUNCH_BASE_RATE = DEFAULT_LUNCH_BASE_RATE;
    public static double TAKEOUT_RATE_AT_LUNCH = DEFAULT_TAKEOUT_RATE_AT_LUNCH;
    public static double[] LUNCH_BATCH_WEIGHTS = DEFAULT_LUNCH_BATCH_WEIGHTS.clone();
    public static int[] LUNCH_RELEASE_TIMES = DEFAULT_LUNCH_RELEASE_TIMES.clone();
    public static int[] LUNCH_PEAK_DELAY_MEANS = DEFAULT_LUNCH_PEAK_DELAY_MEANS.clone();

    public static double DINNER_BASE_RATE = DEFAULT_DINNER_BASE_RATE;
    public static double EVENING_CLASS_RATIO = DEFAULT_EVENING_CLASS_RATIO;
    public static double DINNER_SKIP_OR_OFF_CAMPUS_RATE = DEFAULT_DINNER_SKIP_OR_OFF_CAMPUS_RATE;
    public static int DINNER_UNIFIED_RELEASE_TIME = DEFAULT_DINNER_UNIFIED_RELEASE_TIME;
    public static int DINNER_PEAK_DELAY_MEAN = DEFAULT_DINNER_PEAK_DELAY_MEAN;

    public static double BACKGROUND_FLOW_RATIO = DEFAULT_BACKGROUND_FLOW_RATIO;

    public static int getWindowCount() {
        return WINDOW_DISTANCES.length;
    }

    public static synchronized void initWindowsConfig(int windowCount) {
        if (windowCount <= 0) {
            throw new IllegalArgumentException("windowCount must be greater than 0");
        }

        WINDOW_DISTANCES = new int[windowCount];
        for (int i = 0; i < windowCount; i++) {
            WINDOW_DISTANCES[i] = 10 + i * 5;
        }

        WINDOW_AVG_SERVE_TIME = generateServeTimesForWindows(windowCount, RANDOM_SEED);
    }

    private static int[] generateServeTimesForWindows(int windowCount, long seed) {
        Random random = new Random(seed + windowCount * 131L + 17L);
        int slowCount = Math.max(1, (int) Math.round(windowCount / 3.0));
        if (windowCount == 1) {
            slowCount = 0;
        }
        int fastCount = windowCount - slowCount;

        List<Integer> serveTimes = new ArrayList<>();
        for (int i = 0; i < fastCount; i++) {
            serveTimes.add(randomBetweenInclusive(random, 15, 25));
        }
        for (int i = 0; i < slowCount; i++) {
            serveTimes.add(randomBetweenInclusive(random, 60, 120));
        }

        Collections.shuffle(serveTimes, random);

        int[] result = new int[windowCount];
        for (int i = 0; i < windowCount; i++) {
            result[i] = serveTimes.get(i);
        }
        return result;
    }

    private static int randomBetweenInclusive(Random random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    public static synchronized void updateWindowConfigs(int[] distances, int[] serveTimes) {
        if (distances == null || serveTimes == null) {
            throw new IllegalArgumentException("window config arrays cannot be null");
        }
        if (distances.length == 0 || serveTimes.length == 0) {
            throw new IllegalArgumentException("window config arrays cannot be empty");
        }
        if (distances.length != serveTimes.length) {
            throw new IllegalArgumentException("window distance and serve time arrays must have the same length");
        }

        WINDOW_DISTANCES = distances.clone();
        WINDOW_AVG_SERVE_TIME = serveTimes.clone();

        validate();
    }

    public static synchronized void updateAllConfigs(SimulationConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("config request cannot be null");
        }

        TOTAL_TABLES = request.getTableCount();
        OPEN_DURATION = request.getOpenDuration();
        MEAL_GAP_TICKS = request.getMealGapTicks();
        SNAPSHOT_INTERVAL = request.getSnapshotInterval();
        RANDOM_SEED = request.getRandomSeed();

        DINING_TIME_MEAN = request.getDiningTimeMean();
        DINING_TIME_STD = request.getDiningTimeStd();
        MIN_DINING_TIME = request.getMinDiningTime();

        PATIENCE_MIN = request.getPatienceMin();
        PATIENCE_MAX = request.getPatienceMax();

        PROB_SOLO = request.getProbSolo();
        PROB_DUO = request.getProbDuo();
        PROB_TEAM = request.getProbTeam();

        TOTAL_POPULATION = request.getTotalPopulation();
        SIMULATION_MODE = SimulationMode.fromCode(request.getSimulationMode());
        MEAL_PERIOD = MealPeriod.fromCode(request.getMealPeriod());

        if (request.getWindowDistances() != null && request.getWindowAvgServeTime() != null) {
            updateWindowConfigs(request.getWindowDistances(), request.getWindowAvgServeTime());
        } else {
            initWindowsConfig(request.getWindowCount());
        }

        validate();
    }

    public static synchronized void resetToDefaults() {
        TOTAL_TABLES = DEFAULT_TOTAL_TABLES;
        OPEN_DURATION = DEFAULT_OPEN_DURATION;
        SNAPSHOT_INTERVAL = DEFAULT_SNAPSHOT_INTERVAL;
        RANDOM_SEED = DEFAULT_RANDOM_SEED;

        WINDOW_DISTANCES = DEFAULT_WINDOW_DISTANCES.clone();
        WINDOW_AVG_SERVE_TIME = DEFAULT_WINDOW_AVG_SERVE_TIME.clone();

        DINING_TIME_MEAN = DEFAULT_DINING_TIME_MEAN;
        DINING_TIME_STD = DEFAULT_DINING_TIME_STD;
        MIN_DINING_TIME = DEFAULT_MIN_DINING_TIME;

        PATIENCE_MIN = DEFAULT_PATIENCE_MIN;
        PATIENCE_MAX = DEFAULT_PATIENCE_MAX;

        PROB_SOLO = DEFAULT_PROB_SOLO;
        PROB_DUO = DEFAULT_PROB_DUO;
        PROB_TEAM = DEFAULT_PROB_TEAM;

        TOTAL_POPULATION = DEFAULT_TOTAL_POPULATION;
        SIMULATION_MODE = DEFAULT_SIMULATION_MODE;
        MEAL_PERIOD = DEFAULT_MEAL_PERIOD;

        BREAKFAST_BASE_RATE = DEFAULT_BREAKFAST_BASE_RATE;
        EARLY_CLASS_RATIO = DEFAULT_EARLY_CLASS_RATIO;
        BREAKFAST_SKIP_RATE = DEFAULT_BREAKFAST_SKIP_RATE;
        EARLY_CLASS_BREAKFAST_BOOST = DEFAULT_EARLY_CLASS_BREAKFAST_BOOST;

        LUNCH_BASE_RATE = DEFAULT_LUNCH_BASE_RATE;
        TAKEOUT_RATE_AT_LUNCH = DEFAULT_TAKEOUT_RATE_AT_LUNCH;
        LUNCH_BATCH_WEIGHTS = DEFAULT_LUNCH_BATCH_WEIGHTS.clone();
        LUNCH_RELEASE_TIMES = DEFAULT_LUNCH_RELEASE_TIMES.clone();
        LUNCH_PEAK_DELAY_MEANS = DEFAULT_LUNCH_PEAK_DELAY_MEANS.clone();

        DINNER_BASE_RATE = DEFAULT_DINNER_BASE_RATE;
        EVENING_CLASS_RATIO = DEFAULT_EVENING_CLASS_RATIO;
        DINNER_SKIP_OR_OFF_CAMPUS_RATE = DEFAULT_DINNER_SKIP_OR_OFF_CAMPUS_RATE;
        DINNER_UNIFIED_RELEASE_TIME = DEFAULT_DINNER_UNIFIED_RELEASE_TIME;
        DINNER_PEAK_DELAY_MEAN = DEFAULT_DINNER_PEAK_DELAY_MEAN;

        BACKGROUND_FLOW_RATIO = DEFAULT_BACKGROUND_FLOW_RATIO;

        validate();
    }

    public static synchronized void validate() {
        if (TOTAL_TABLES <= 0) {
            throw new IllegalArgumentException("table count must be greater than 0");
        }
        if (WINDOW_DISTANCES == null || WINDOW_AVG_SERVE_TIME == null) {
            throw new IllegalArgumentException("window config arrays cannot be null");
        }
        if (WINDOW_DISTANCES.length != WINDOW_AVG_SERVE_TIME.length) {
            throw new IllegalArgumentException("window config arrays must have the same length");
        }
        if (WINDOW_DISTANCES.length == 0) {
            throw new IllegalArgumentException("window count cannot be 0");
        }
        for (int distance : WINDOW_DISTANCES) {
            if (distance < 0) {
                throw new IllegalArgumentException("window distance cannot be negative");
            }
        }
        for (int serveTime : WINDOW_AVG_SERVE_TIME) {
            if (serveTime <= 0) {
                throw new IllegalArgumentException("window serve time must be greater than 0");
            }
        }
        if (OPEN_DURATION <= 0) {
            throw new IllegalArgumentException("open duration must be greater than 0");
        }
        if (MEAL_GAP_TICKS < 0) {
            throw new IllegalArgumentException("meal gap ticks must be >= 0");
        }
        if (SNAPSHOT_INTERVAL <= 0) {
            throw new IllegalArgumentException("snapshot interval must be greater than 0");
        }
        if (PATIENCE_MIN < 0 || PATIENCE_MAX < 0 || PATIENCE_MIN > PATIENCE_MAX) {
            throw new IllegalArgumentException("invalid patience range");
        }
        if (DINING_TIME_MEAN <= 0 || DINING_TIME_STD < 0 || MIN_DINING_TIME <= 0) {
            throw new IllegalArgumentException("invalid dining time config");
        }

        double groupProbabilitySum = PROB_SOLO + PROB_DUO + PROB_TEAM;
        if (Math.abs(groupProbabilitySum - 1.0) > 1e-9) {
            throw new IllegalArgumentException("group probabilities must sum to 1.0, current: " + groupProbabilitySum);
        }

        if (TOTAL_POPULATION <= 0) {
            throw new IllegalArgumentException("totalPopulation must be greater than 0");
        }
        validateRate(BREAKFAST_BASE_RATE, "breakfastBaseRate");
        validateRate(EARLY_CLASS_RATIO, "earlyClassRatio");
        validateRate(BREAKFAST_SKIP_RATE, "breakfastSkipRate");
        validateRate(EARLY_CLASS_BREAKFAST_BOOST, "earlyClassBreakfastBoost");
        validateRate(LUNCH_BASE_RATE, "lunchBaseRate");
        validateRate(TAKEOUT_RATE_AT_LUNCH, "takeoutRateAtLunch");
        validateRate(DINNER_BASE_RATE, "dinnerBaseRate");
        validateRate(EVENING_CLASS_RATIO, "eveningClassRatio");
        validateRate(DINNER_SKIP_OR_OFF_CAMPUS_RATE, "dinnerSkipOrOffCampusRate");
        validateRate(BACKGROUND_FLOW_RATIO, "backgroundFlowRatio");

        if (LUNCH_BATCH_WEIGHTS == null || LUNCH_RELEASE_TIMES == null || LUNCH_PEAK_DELAY_MEANS == null) {
            throw new IllegalArgumentException("lunch batch config cannot be null");
        }
        if (LUNCH_BATCH_WEIGHTS.length == 0
                || LUNCH_BATCH_WEIGHTS.length != LUNCH_RELEASE_TIMES.length
                || LUNCH_BATCH_WEIGHTS.length != LUNCH_PEAK_DELAY_MEANS.length) {
            throw new IllegalArgumentException("lunch batch config arrays must be non-empty and have the same length");
        }
        double batchWeightSum = 0.0;
        for (double weight : LUNCH_BATCH_WEIGHTS) {
            if (weight < 0) {
                throw new IllegalArgumentException("lunch batch weights cannot be negative");
            }
            batchWeightSum += weight;
        }
        if (batchWeightSum <= 0.0) {
            throw new IllegalArgumentException("lunch batch weights must have a positive sum");
        }
    }

    private static void validateRate(double value, String name) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }

    public static synchronized String dumpConfig() {
        return "CanteenConfig{" +
                "TOTAL_TABLES=" + TOTAL_TABLES +
                ", OPEN_DURATION=" + OPEN_DURATION +
                ", MEAL_GAP_TICKS=" + MEAL_GAP_TICKS +
                ", SNAPSHOT_INTERVAL=" + SNAPSHOT_INTERVAL +
                ", RANDOM_SEED=" + RANDOM_SEED +
                ", WINDOW_DISTANCES=" + Arrays.toString(WINDOW_DISTANCES) +
                ", WINDOW_AVG_SERVE_TIME_SECONDS=" + Arrays.toString(WINDOW_AVG_SERVE_TIME) +
                ", DINING_TIME_MEAN_SECONDS=" + DINING_TIME_MEAN +
                ", DINING_TIME_STD_SECONDS=" + DINING_TIME_STD +
                ", MIN_DINING_TIME_SECONDS=" + MIN_DINING_TIME +
                ", PATIENCE_MIN_SECONDS=" + PATIENCE_MIN +
                ", PATIENCE_MAX_SECONDS=" + PATIENCE_MAX +
                ", PROB_SOLO=" + PROB_SOLO +
                ", PROB_DUO=" + PROB_DUO +
                ", PROB_TEAM=" + PROB_TEAM +
                ", TOTAL_POPULATION=" + TOTAL_POPULATION +
                ", SIMULATION_MODE=" + SIMULATION_MODE +
                ", MEAL_PERIOD=" + MEAL_PERIOD +
                '}';
    }
}
