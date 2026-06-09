package backend.optimize;

import backend.dto.OptimizationMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class OptimizeResult {
    public SimRunResult bestResult;
    public SimRunResult currentResult;
    public double bestLoss = Double.MAX_VALUE;

    /**
     * Frontend-ready full process data:
     * best card uses bestResult; loss/current-best curves and window-table heatmaps use allResults.
     */
    public List<SimRunResult> allResults = new ArrayList<>();
    public List<SimRunResult> topKResults = new ArrayList<>();
    public ReplayResult replayResult;
    public long totalRuntimeMs;
    public int totalCandidateCount;
    public int totalSimulationCount;
    public int searchRounds;
    public String stopReason = "";
    public List<String> rangeHistory = new ArrayList<>();

    public void buildTopK(int k) {
        buildTopK(k, OptimizationMode.BALANCED);
    }

    public void buildTopK(int k, OptimizationMode mode) {
        buildTopK(k, mode, false);
    }

    public void buildTopK(int k, OptimizationMode mode, boolean onlyShowServiceLevelPassed) {
        List<SimRunResult> source = allResults;
        if (onlyShowServiceLevelPassed) {
            List<SimRunResult> passed = allResults.stream()
                    .filter(result -> result.serviceLevelPassed)
                    .collect(Collectors.toList());
            if (!passed.isEmpty()) {
                source = passed;
            }
        }

        topKResults = source.stream()
                .sorted(comparatorFor(mode))
                .limit(k)
                .map(SimRunResult::copyBasic)
                .collect(Collectors.toList());
        if (!topKResults.isEmpty()) {
            bestResult = topKResults.get(0).copyBasic();
            bestLoss = bestResult.loss;
        }
    }

    public boolean isBetter(SimRunResult candidate, OptimizationMode mode) {
        return candidate != null
                && (bestResult == null || comparatorFor(mode).compare(candidate, bestResult) < 0);
    }

    public static Comparator<SimRunResult> comparatorFor(OptimizationMode mode) {
        OptimizationMode resolved = mode == null ? OptimizationMode.BALANCED : mode;
        return (left, right) -> {
            if (resolved == OptimizationMode.PROFIT_FIRST) {
                return firstNonZero(
                        desc(left.netProfit, right.netProfit),
                        desc(left.finishRate, right.finishRate),
                        asc(left.avgWaitTimeMinutes, right.avgWaitTimeMinutes),
                        asc(left.avgSeatWaitTimeMinutes, right.avgSeatWaitTimeMinutes),
                        serviceTie(left, right),
                        desc(left.score, right.score),
                        asc(left.loss, right.loss)
                );
            }
            if (resolved == OptimizationMode.COMPLETION_FIRST) {
                return firstNonZero(
                        desc(left.finishRate, right.finishRate),
                        asc(left.abandonedStudents, right.abandonedStudents),
                        asc(left.avgWaitTimeMinutes, right.avgWaitTimeMinutes),
                        asc(left.avgSeatWaitTimeMinutes, right.avgSeatWaitTimeMinutes),
                        desc(left.netProfit, right.netProfit),
                        serviceTie(left, right),
                        desc(left.score, right.score),
                        asc(left.loss, right.loss)
                );
            }
            if (resolved == OptimizationMode.EXPERIENCE_FIRST) {
                return firstNonZero(
                        asc(left.avgWaitTimeMinutes + left.avgSeatWaitTimeMinutes,
                                right.avgWaitTimeMinutes + right.avgSeatWaitTimeMinutes),
                        asc(left.avgWaitTimeMinutes, right.avgWaitTimeMinutes),
                        asc(left.avgSeatWaitTimeMinutes, right.avgSeatWaitTimeMinutes),
                        desc(left.finishRate, right.finishRate),
                        desc(left.netProfit, right.netProfit),
                        serviceTie(left, right),
                        desc(left.score, right.score),
                        asc(left.loss, right.loss)
                );
            }
            return firstNonZero(
                    desc(left.score, right.score),
                    desc(left.finishRate, right.finishRate),
                    desc(left.netProfit, right.netProfit),
                    asc(left.avgWaitTimeMinutes, right.avgWaitTimeMinutes),
                    asc(left.avgSeatWaitTimeMinutes, right.avgSeatWaitTimeMinutes),
                    serviceTie(left, right),
                    asc(left.loss, right.loss)
            );
        };
    }

    private static int serviceTie(SimRunResult left, SimRunResult right) {
        return Boolean.compare(right.serviceLevelPassed, left.serviceLevelPassed);
    }

    private static int firstNonZero(int... values) {
        for (int value : values) {
            if (value != 0) {
                return value;
            }
        }
        return 0;
    }

    private static int desc(double left, double right) {
        return Double.compare(right, left);
    }

    private static int asc(double left, double right) {
        return Double.compare(left, right);
    }

    private static int asc(int left, int right) {
        return Integer.compare(left, right);
    }
}
