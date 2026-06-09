package frontend;

import backend.config.CanteenConfig;
import backend.dto.OptimizationMode;
import backend.model.MealPeriod;
import backend.model.SimulationMode;
import backend.optimize.LossConfig;
import backend.optimize.OptimizeConfig;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared advanced settings for optimization and full-day population planning.
 * A zero search bound means "auto".
 */
public final class AdvancedOptimizationSettings {
    public static boolean useFixedRandomSeed;
    public static long fixedRandomSeed;
    public static int minWindowCount;
    public static int maxWindowCount;
    public static int minTableCount;
    public static int maxTableCount;
    public static int currentWindowCount;
    public static int currentTableCount;
    public static int repeatTimes;
    public static int maxCandidateEvaluations;
    public static int localRestartCount;
    public static int topK;
    public static String optimizationModeDisplayName;

    public static double avgMealPrice;
    public static double windowCostPerHour;
    public static double tableCost;
    public static double lostStudentPenalty;

    public static double breakfastPopulationRatio;
    public static double lunchPopulationRatio;
    public static double dinnerPopulationRatio;

    public static double waitWeight;
    public static double seatWaitWeight;
    public static double queueWeight;
    public static double abandonWeight;
    public static double crowdingWeight;
    public static double minAcceptFinishRate;
    public static double hardWaitThresholdMinutes;
    public static double hardSeatWaitThresholdMinutes;
    public static double hardQueueThresholdLength;
    public static double hardAbandonThresholdRate;

    static {
        resetDefaults();
    }

    private AdvancedOptimizationSettings() {
    }

    public static void resetDefaults() {
        useFixedRandomSeed = false;
        fixedRandomSeed = CanteenConfig.DEFAULT_RANDOM_SEED;
        minWindowCount = 0;
        maxWindowCount = 0;
        minTableCount = 0;
        maxTableCount = 0;
        currentWindowCount = 0;
        currentTableCount = 0;
        repeatTimes = 3;
        maxCandidateEvaluations = 400;
        localRestartCount = 3;
        topK = 10;
        optimizationModeDisplayName = OptimizationMode.BALANCED.getDisplayName();

        avgMealPrice = CanteenConfig.DEFAULT_AVG_MEAL_PRICE;
        windowCostPerHour = CanteenConfig.DEFAULT_WINDOW_COST_PER_HOUR;
        tableCost = CanteenConfig.DEFAULT_TABLE_COST;
        lostStudentPenalty = CanteenConfig.DEFAULT_LOST_STUDENT_PENALTY;

        breakfastPopulationRatio = CanteenConfig.DEFAULT_BREAKFAST_POPULATION_RATIO;
        lunchPopulationRatio = CanteenConfig.DEFAULT_LUNCH_POPULATION_RATIO;
        dinnerPopulationRatio = CanteenConfig.DEFAULT_DINNER_POPULATION_RATIO;

        LossConfig loss = new LossConfig();
        waitWeight = loss.waitWeight;
        seatWaitWeight = loss.seatWaitWeight;
        queueWeight = loss.queueWeight;
        abandonWeight = loss.abandonWeight;
        crowdingWeight = loss.crowdingWeight;
        minAcceptFinishRate = loss.minAcceptFinishRate;
        hardWaitThresholdMinutes = loss.hardWaitThresholdMinutes;
        hardSeatWaitThresholdMinutes = loss.hardSeatWaitThresholdMinutes;
        hardQueueThresholdLength = loss.hardQueueThresholdLength;
        hardAbandonThresholdRate = loss.hardAbandonThresholdRate;
    }

    public static void applyTo(OptimizeConfig config) {
        int practicalWindowCount = estimatePracticalWindowCount(config.totalPopulation);
        int initialWindowMin = 1;
        int initialWindowMax = Math.max(4, (int) Math.ceil(practicalWindowCount * 1.25));
        int initialTableMin = OptimizeConfig.defaultMinTableCount(
                config.totalPopulation,
                breakfastPopulationRatio,
                lunchPopulationRatio,
                dinnerPopulationRatio
        );
        int initialTableMax = OptimizeConfig.defaultMaxTableCount(
                config.totalPopulation,
                breakfastPopulationRatio,
                lunchPopulationRatio,
                dinnerPopulationRatio
        );
        int windowLimit = Math.max(20, Math.max(
                (int) Math.ceil(practicalWindowCount * 2.0),
                (int) Math.ceil(config.totalPopulation / 150.0)
        ));
        int tableLimit = OptimizeConfig.defaultTableLimit(
                config.totalPopulation,
                breakfastPopulationRatio,
                lunchPopulationRatio,
                dinnerPopulationRatio
        );

        boolean hasWindowMin = minWindowCount > 0;
        boolean hasWindowMax = maxWindowCount > 0;
        boolean hasTableMin = minTableCount > 0;
        boolean hasTableMax = maxTableCount > 0;

        config.minWindowCount = hasWindowMin ? minWindowCount : initialWindowMin;
        config.maxWindowCount = hasWindowMax ? maxWindowCount : initialWindowMax;
        config.minTableCount = hasTableMin ? minTableCount : initialTableMin;
        config.maxTableCount = hasTableMax ? maxTableCount : initialTableMax;
        if (config.maxWindowCount < config.minWindowCount) {
            config.maxWindowCount = config.minWindowCount;
        }
        if (config.maxTableCount < config.minTableCount) {
            config.maxTableCount = config.minTableCount;
        }
        config.maxWindowLimit = hasWindowMax ? config.maxWindowCount : Math.max(windowLimit, config.maxWindowCount);
        config.maxTableLimit = hasTableMax ? config.maxTableCount : Math.max(tableLimit, config.maxTableCount);

        config.currentWindowCount = currentWindowCount > 0
                ? currentWindowCount
                : clamp(CanteenConfig.getWindowCount(), config.minWindowCount, config.maxWindowCount);
        config.currentTableCount = currentTableCount > 0
                ? currentTableCount
                : clamp(CanteenConfig.TOTAL_TABLES, config.minTableCount, config.maxTableCount);
        config.repeatTimes = repeatTimes;
        config.maxCandidateEvaluations = maxCandidateEvaluations;
        config.localRestartCount = localRestartCount;
        config.topK = topK;
        config.baseRandomSeed = useFixedRandomSeed ? fixedRandomSeed : nextAutomaticRandomSeed();
        config.avgMealPrice = avgMealPrice;
        config.windowCostPerHour = windowCostPerHour;
        config.tableCost = tableCost;
        config.lostStudentPenalty = lostStudentPenalty;
        config.optimizationMode = OptimizationMode.fromDisplayName(optimizationModeDisplayName);
    }

    public static long nextAutomaticRandomSeed() {
        return ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
    }

    private static int estimatePracticalWindowCount(int totalPopulation) {
        double peakPopulation = estimatePeakMealPopulation(totalPopulation);
        double openSeconds = Math.max(60.0, CanteenConfig.OPEN_DURATION * 60.0);
        double capacityPerWindow = Math.max(1.0, openSeconds / averageServeSeconds());
        return Math.max(1, (int) Math.ceil(peakPopulation / capacityPerWindow / 0.85));
    }

    private static double estimatePeakMealPopulation(int totalPopulation) {
        double breakfast = Math.max(0.0, breakfastPopulationRatio);
        double lunch = Math.max(0.0, lunchPopulationRatio);
        double dinner = Math.max(0.0, dinnerPopulationRatio);
        double sum = breakfast + lunch + dinner;
        if (sum <= 0.0) {
            breakfast = CanteenConfig.DEFAULT_BREAKFAST_POPULATION_RATIO;
            lunch = CanteenConfig.DEFAULT_LUNCH_POPULATION_RATIO;
            dinner = CanteenConfig.DEFAULT_DINNER_POPULATION_RATIO;
            sum = breakfast + lunch + dinner;
        }
        double peakRatio = Math.max(breakfast, Math.max(lunch, dinner)) / sum;
        return totalPopulation * peakRatio;
    }

    private static double averageServeSeconds() {
        int[] serveTimes = CanteenConfig.WINDOW_AVG_SERVE_TIME;
        if (serveTimes == null || serveTimes.length == 0) {
            serveTimes = CanteenConfig.DEFAULT_WINDOW_AVG_SERVE_TIME;
        }
        double sum = 0.0;
        for (int serveTime : serveTimes) {
            sum += Math.max(1, serveTime);
        }
        return Math.max(1.0, sum / serveTimes.length);
    }

    public static LossConfig buildLossConfig() {
        LossConfig config = new LossConfig();
        config.waitWeight = waitWeight;
        config.seatWaitWeight = seatWaitWeight;
        config.queueWeight = queueWeight;
        config.abandonWeight = abandonWeight;
        config.crowdingWeight = crowdingWeight;
        config.minAcceptFinishRate = minAcceptFinishRate;
        config.hardWaitThresholdMinutes = hardWaitThresholdMinutes;
        config.hardSeatWaitThresholdMinutes = hardSeatWaitThresholdMinutes;
        config.hardQueueThresholdLength = hardQueueThresholdLength;
        config.hardAbandonThresholdRate = hardAbandonThresholdRate;
        return config;
    }

    public static void applyRuntimeProfileForOptimization(int totalPopulation) {
        CanteenConfig.TOTAL_POPULATION = totalPopulation;
        CanteenConfig.SIMULATION_MODE = SimulationMode.FULL_DAY;
        CanteenConfig.MEAL_PERIOD = MealPeriod.LUNCH;
        CanteenConfig.AVG_MEAL_PRICE = avgMealPrice;
        CanteenConfig.WINDOW_COST_PER_HOUR = windowCostPerHour;
        CanteenConfig.TABLE_COST = tableCost;
        CanteenConfig.LOST_STUDENT_PENALTY = lostStudentPenalty;
        applyPopulationRatiosToRuntime();
        CanteenConfig.validate();
    }

    public static void applyPopulationRatiosToRuntime() {
        CanteenConfig.BREAKFAST_POPULATION_RATIO = breakfastPopulationRatio;
        CanteenConfig.LUNCH_POPULATION_RATIO = lunchPopulationRatio;
        CanteenConfig.DINNER_POPULATION_RATIO = dinnerPopulationRatio;
    }

    public static int populationForPeriod(int totalPopulation, String mealPeriodCode) {
        double[] normalized = normalizedPopulationRatios();
        if ("breakfast".equals(mealPeriodCode)) {
            return Math.max(1, (int) Math.round(totalPopulation * normalized[0]));
        }
        if ("dinner".equals(mealPeriodCode)) {
            return Math.max(1, (int) Math.round(totalPopulation * normalized[2]));
        }
        return Math.max(1, totalPopulation
                - (int) Math.round(totalPopulation * normalized[0])
                - (int) Math.round(totalPopulation * normalized[2]));
    }

    private static double[] normalizedPopulationRatios() {
        double breakfast = Math.max(0.0, breakfastPopulationRatio);
        double lunch = Math.max(0.0, lunchPopulationRatio);
        double dinner = Math.max(0.0, dinnerPopulationRatio);
        double sum = breakfast + lunch + dinner;
        if (sum <= 0.0) {
            breakfast = CanteenConfig.DEFAULT_BREAKFAST_POPULATION_RATIO;
            lunch = CanteenConfig.DEFAULT_LUNCH_POPULATION_RATIO;
            dinner = CanteenConfig.DEFAULT_DINNER_POPULATION_RATIO;
            sum = breakfast + lunch + dinner;
        }
        return new double[]{breakfast / sum, lunch / sum, dinner / sum};
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
