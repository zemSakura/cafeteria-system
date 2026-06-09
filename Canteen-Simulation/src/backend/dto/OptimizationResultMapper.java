package backend.dto;

import backend.optimize.OptimizeConfig;
import backend.optimize.OptimizeResult;
import backend.optimize.ReplaySnapshot;
import backend.optimize.SimRunResult;

import java.util.List;
import java.util.Locale;

/**
 * Separates optimizer internals from the frontend data contract.
 */
public final class OptimizationResultMapper {
    private OptimizationResultMapper() {
    }

    public static backend.dto.OptimizationResult from(OptimizeResult source, OptimizeConfig config) {
        backend.dto.OptimizationResult dto = new backend.dto.OptimizationResult();
        if (source == null) {
            return dto;
        }

        dto.recommendedPlan = toPlan(source.bestResult, 1);
        dto.currentPlan = toPlan(source.currentResult, 0);

        int rank = 1;
        for (SimRunResult result : source.topKResults) {
            dto.topKPlans.add(toPlan(result, rank++));
        }
        for (SimRunResult result : source.allResults) {
            HeatmapPoint point = new HeatmapPoint();
            point.windowCount = result.windowCount;
            point.tableCount = result.tableCount;
            point.score = result.score;
            point.completionRate = result.finishRate;
            point.netProfit = result.netProfit;
            dto.heatmapData.add(point);
        }
        if (source.replayResult != null) {
            for (ReplaySnapshot replay : source.replayResult.snapshots) {
                TrendPoint point = new TrendPoint();
                point.timeSecond = replay.timeSecond;
                point.queueingCount = replay.totalQueueLength;
                point.waitingSeatCount = replay.waitingSeatStudents;
                point.windowUtilizationRate = replay.windowUtilization;
                point.tableUtilizationRate = replay.tableUtilization;
                point.completedCount = replay.finishedStudents;
                point.abandonedCount = replay.abandonedStudents;
                dto.trendData.add(point);
            }
        }

        dto.comparison = compare(dto.currentPlan, dto.recommendedPlan);
        if (dto.recommendedPlan != null && dto.comparison != null) {
            dto.recommendedPlan.reason = dto.comparison.summary;
        }
        return dto;
    }

    private static PlanResult toPlan(SimRunResult result, int rank) {
        if (result == null) {
            return null;
        }
        PlanResult plan = new PlanResult();
        plan.rank = rank;
        plan.windowCount = result.windowCount;
        plan.tableCount = result.tableCount;
        plan.completionRate = result.finishRate;
        plan.netProfit = result.netProfit;
        plan.avgQueueWait = result.avgWaitTimeMinutes;
        plan.avgSeatWait = result.avgSeatWaitTimeMinutes;
        plan.abandonedCount = result.abandonedStudents;
        plan.windowUtilization = result.windowUtilization;
        plan.tableUtilization = result.tableUtilization;
        plan.score = result.score;
        plan.reason = result.reason == null ? "" : result.reason;
        return plan;
    }

    private static ComparisonResult compare(PlanResult current, PlanResult recommended) {
        if (current == null || recommended == null) {
            return null;
        }
        ComparisonResult comparison = new ComparisonResult();
        comparison.completionRateDelta = recommended.completionRate - current.completionRate;
        comparison.netProfitDelta = recommended.netProfit - current.netProfit;
        comparison.avgQueueWaitDelta = recommended.avgQueueWait - current.avgQueueWait;
        comparison.avgSeatWaitDelta = recommended.avgSeatWait - current.avgSeatWait;
        comparison.abandonedCountDelta = recommended.abandonedCount - current.abandonedCount;
        comparison.scoreDelta = recommended.score - current.score;
        comparison.summary = String.format(
                Locale.US,
                "较当前方案：完成率 %+.1f%%，净收益 %+.2f 元，放弃人数 %d，排队等待 %+.2f 分钟",
                comparison.completionRateDelta * 100.0,
                comparison.netProfitDelta,
                comparison.abandonedCountDelta,
                comparison.avgQueueWaitDelta
        );
        return comparison;
    }
}
