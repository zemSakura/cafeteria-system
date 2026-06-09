package backend.optimize;

import backend.config.CanteenConfig;
import backend.dto.OptimizationMode;

public class OptimizeConfig {
    public static final int SEATS_PER_TABLE = 4;
    public static final double TARGET_SEAT_UTILIZATION = 0.85;
    private static final double DEFAULT_TABLE_MIN_FACTOR = 0.90;
    private static final double DEFAULT_TABLE_MAX_FACTOR = 1.80;
    private static final double DEFAULT_TABLE_LIMIT_FACTOR = 3.00;

    public int totalPopulation = 1000;
    public int minWindowCount = 1;
    public int maxWindowCount = 4;
    public int minTableCount = defaultMinTableCount(totalPopulation);
    public int maxTableCount = defaultMaxTableCount(totalPopulation);
    public int repeatTimes = 3;
    public long baseRandomSeed = 20260525L;
    public boolean exportCsv = true;
    public int topK = 10;
    public boolean onlyShowServiceLevelPassed = false;
    public boolean runReplayAfterOptimization = true;
    public int replaySnapshotIntervalSeconds = 60;
    public boolean verboseConsoleLog = true;
    public boolean useAdaptiveBoundarySearch = true;
    public boolean useConvergentSearch = true;
    public int maxCandidateEvaluations = 400;
    public int stagnationPatience = 4;
    public double minLossImprovement = 0.0001;
    public int globalProbeCount = 9;
    public int localRestartCount = 3;
    public int windowExpandStep = 2;
    public int tableExpandStep = 40;
    public int windowBoundaryMargin = 1;
    public int tableBoundaryMargin = 20;
    public double minImprovementRatio = 0.01;
    public int stableRoundLimit = 2;
    public int maxAutoRounds = 10;
    public int maxWindowLimit = 0;
    public int maxTableLimit = 0;
    public int adaptiveTableScanStep = 40;
    public int finalValidationTableStep = 20;
    public int currentWindowCount = 5;
    public int currentTableCount = 90;
    public double avgMealPrice = CanteenConfig.DEFAULT_AVG_MEAL_PRICE;
    public double windowCostPerHour = CanteenConfig.DEFAULT_WINDOW_COST_PER_HOUR;
    public double tableCost = CanteenConfig.DEFAULT_TABLE_COST;
    public double lostStudentPenalty = CanteenConfig.DEFAULT_LOST_STUDENT_PENALTY;
    public OptimizationMode optimizationMode = OptimizationMode.PROFIT_FIRST;

    public void validate() {
        if (totalPopulation <= 0) {
            throw new IllegalArgumentException("就餐人数必须大于 0");
        }
        if (minWindowCount <= 0 || maxWindowCount < minWindowCount) {
            throw new IllegalArgumentException("窗口数量搜索范围不合法");
        }
        if (minTableCount <= 0 || maxTableCount < minTableCount) {
            throw new IllegalArgumentException("桌子数量搜索范围不合法");
        }
        if (repeatTimes <= 0) {
            throw new IllegalArgumentException("重复次数必须大于 0");
        }
        if (topK <= 0) {
            topK = 10;
        }
        if (maxCandidateEvaluations <= 0) {
            maxCandidateEvaluations = 180;
        }
        if (stagnationPatience <= 0) {
            stagnationPatience = 4;
        }
        if (globalProbeCount <= 0) {
            globalProbeCount = 9;
        }
        if (localRestartCount <= 0) {
            localRestartCount = 3;
        }
        if (minLossImprovement < 0.0) {
            minLossImprovement = 0.0;
        }
        if (windowExpandStep <= 0) {
            windowExpandStep = 2;
        }
        if (tableExpandStep <= 0) {
            tableExpandStep = 40;
        }
        if (windowBoundaryMargin < 0) {
            windowBoundaryMargin = 1;
        }
        if (tableBoundaryMargin < 0) {
            tableBoundaryMargin = 20;
        }
        if (minImprovementRatio < 0.0) {
            minImprovementRatio = 0.01;
        }
        if (stableRoundLimit <= 0) {
            stableRoundLimit = 2;
        }
        if (maxAutoRounds <= 0) {
            maxAutoRounds = 10;
        }
        if (maxWindowLimit <= 0) {
            maxWindowLimit = Math.max(20, Math.max(
                    maxWindowCount,
                    (int) Math.ceil(totalPopulation / 150.0)
            ));
        }
        if (maxTableLimit <= 0) {
            maxTableLimit = defaultTableLimit(totalPopulation);
        }
        if (adaptiveTableScanStep <= 0) {
            adaptiveTableScanStep = 40;
        }
        if (finalValidationTableStep <= 0) {
            finalValidationTableStep = Math.max(10, tableExpandStep / 2);
        }
        if (currentWindowCount <= 0) {
            currentWindowCount = minWindowCount;
        }
        if (currentTableCount <= 0) {
            currentTableCount = minTableCount;
        }
        if (avgMealPrice < 0.0 || windowCostPerHour < 0.0 || tableCost < 0.0 || lostStudentPenalty < 0.0) {
            throw new IllegalArgumentException("经营参数不能为负数");
        }
        if (optimizationMode == null) {
            optimizationMode = OptimizationMode.BALANCED;
        }
        if (replaySnapshotIntervalSeconds <= 0) {
            replaySnapshotIntervalSeconds = 60;
        }
    }

    public int totalCandidateCount() {
        if (useAdaptiveBoundarySearch) {
            return maxCandidateEvaluations;
        }
        if (useConvergentSearch) {
            return Math.min(maxCandidateEvaluations, fullGridCandidateCount());
        }
        return fullGridCandidateCount();
    }

    public int adaptiveCandidateUpperBound() {
        int windowCells = Math.max(1, maxWindowCount - minWindowCount + 1);
        int tableStep = Math.max(1, adaptiveTableScanStep);
        int tableRange = Math.max(0, maxTableCount - minTableCount);
        int tableCells = Math.max(1, (tableRange + tableStep - 1) / tableStep + 1);
        return Math.max(1, windowCells * tableCells);
    }

    public int fullGridCandidateCount() {
        return (maxWindowCount - minWindowCount + 1) * (maxTableCount - minTableCount + 1);
    }

    public int totalSimulationCount() {
        return totalCandidateCount() * repeatTimes;
    }

    public static int defaultMinTableCount(int totalPopulation) {
        return defaultMinTableCount(
                totalPopulation,
                CanteenConfig.BREAKFAST_POPULATION_RATIO,
                CanteenConfig.LUNCH_POPULATION_RATIO,
                CanteenConfig.DINNER_POPULATION_RATIO
        );
    }

    public static int defaultMinTableCount(int totalPopulation,
                                           double breakfastRatio,
                                           double lunchRatio,
                                           double dinnerRatio) {
        int practicalCount = estimatePracticalTableCount(totalPopulation, breakfastRatio, lunchRatio, dinnerRatio);
        return Math.max(10, (int) Math.ceil(practicalCount * DEFAULT_TABLE_MIN_FACTOR));
    }

    public static int defaultMaxTableCount(int totalPopulation) {
        return defaultMaxTableCount(
                totalPopulation,
                CanteenConfig.BREAKFAST_POPULATION_RATIO,
                CanteenConfig.LUNCH_POPULATION_RATIO,
                CanteenConfig.DINNER_POPULATION_RATIO
        );
    }

    public static int defaultMaxTableCount(int totalPopulation,
                                           double breakfastRatio,
                                           double lunchRatio,
                                           double dinnerRatio) {
        int practicalCount = estimatePracticalTableCount(totalPopulation, breakfastRatio, lunchRatio, dinnerRatio);
        return Math.max(50, (int) Math.ceil(practicalCount * DEFAULT_TABLE_MAX_FACTOR));
    }

    public static int defaultTableLimit(int totalPopulation) {
        return defaultTableLimit(
                totalPopulation,
                CanteenConfig.BREAKFAST_POPULATION_RATIO,
                CanteenConfig.LUNCH_POPULATION_RATIO,
                CanteenConfig.DINNER_POPULATION_RATIO
        );
    }

    public static int defaultTableLimit(int totalPopulation,
                                        double breakfastRatio,
                                        double lunchRatio,
                                        double dinnerRatio) {
        int practicalCount = estimatePracticalTableCount(totalPopulation, breakfastRatio, lunchRatio, dinnerRatio);
        return Math.max(defaultMaxTableCount(totalPopulation, breakfastRatio, lunchRatio, dinnerRatio),
                Math.max(300, (int) Math.ceil(practicalCount * DEFAULT_TABLE_LIMIT_FACTOR)));
    }

    public static int estimatePracticalTableCount(int totalPopulation) {
        return estimatePracticalTableCount(
                totalPopulation,
                CanteenConfig.BREAKFAST_POPULATION_RATIO,
                CanteenConfig.LUNCH_POPULATION_RATIO,
                CanteenConfig.DINNER_POPULATION_RATIO
        );
    }

    public static int estimatePracticalTableCount(int totalPopulation,
                                                  double breakfastRatio,
                                                  double lunchRatio,
                                                  double dinnerRatio) {
        double peakPopulation = estimatePeakMealPopulation(totalPopulation, breakfastRatio, lunchRatio, dinnerRatio);
        double openMinutes = Math.max(1.0, CanteenConfig.OPEN_DURATION);
        double diningMinutes = Math.max(1.0, CanteenConfig.DINING_TIME_MEAN / 60.0);
        double requiredTables = peakPopulation * diningMinutes
                / openMinutes
                / TARGET_SEAT_UTILIZATION
                / SEATS_PER_TABLE;
        return Math.max(1, (int) Math.ceil(requiredTables));
    }

    private static double estimatePeakMealPopulation(int totalPopulation,
                                                     double breakfastRatio,
                                                     double lunchRatio,
                                                     double dinnerRatio) {
        double breakfast = Math.max(0.0, breakfastRatio);
        double lunch = Math.max(0.0, lunchRatio);
        double dinner = Math.max(0.0, dinnerRatio);
        double sum = breakfast + lunch + dinner;
        if (sum <= 0.0) {
            breakfast = CanteenConfig.DEFAULT_BREAKFAST_POPULATION_RATIO;
            lunch = CanteenConfig.DEFAULT_LUNCH_POPULATION_RATIO;
            dinner = CanteenConfig.DEFAULT_DINNER_POPULATION_RATIO;
            sum = breakfast + lunch + dinner;
        }
        return totalPopulation * Math.max(breakfast, Math.max(lunch, dinner)) / sum;
    }
}
