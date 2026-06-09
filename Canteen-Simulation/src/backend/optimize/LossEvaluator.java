package backend.optimize;

import backend.dto.OptimizationMode;

import java.util.Locale;

public class LossEvaluator {
    public double evaluate(SimRunResult r, LossConfig c) {
        c.validate();
        double p95WaitMinutes = r.p95WaitTimeMinutes > 0.0
                ? r.p95WaitTimeMinutes
                : r.avgWaitTimeMinutes;
        double p95SeatWaitMinutes = r.p95SeatWaitTimeMinutes > 0.0
                ? r.p95SeatWaitTimeMinutes
                : r.avgSeatWaitTimeMinutes;
        applyEconomics(r, c);

        r.rawOperatingCost = r.windowOperatingCost + r.tableOperatingCost;
        double waitNorm = p95WaitMinutes / c.maxAcceptWaitMinutes;
        double seatWaitNorm = p95SeatWaitMinutes / c.maxAcceptWaitMinutes;
        double queueNorm = r.maxQueueLength / c.maxAcceptQueueLength;
        double abandonNorm = r.abandonRate / c.maxAcceptAbandonRate;
        double crowdingNorm = overThreshold(r.seatUtilization, c.comfortableSeatUtilization)
                / c.seatCrowdingScale;

        double resourceCost = c.windowCost * r.windowCount + c.tableCost * r.tableCount;
        r.costLoss = c.costWeight * resourceCost / c.maxAcceptCost;
        r.waitLoss = c.waitWeight * waitNorm;
        r.seatWaitLoss = c.seatWaitWeight * seatWaitNorm;
        r.queueLoss = c.queueWeight * queueNorm;
        r.abandonLoss = c.abandonWeight * abandonNorm;
        r.crowdingLoss = c.crowdingWeight * crowdingNorm;
        r.experienceLoss = c.experienceWeight * (
                r.waitLoss
                        + r.seatWaitLoss
                        + r.queueLoss
                        + r.abandonLoss
                        + r.crowdingLoss
        );
        r.hardConstraintPenalty = calculateHardConstraintPenalty(r, c, p95WaitMinutes, p95SeatWaitMinutes);
        r.finishRatePenalty = r.finishRate < c.minAcceptFinishRate
                ? c.lowFinishRatePenalty * (c.minAcceptFinishRate - r.finishRate)
                : 0.0;

        double loss = r.costLoss
                + r.experienceLoss
                + r.hardConstraintPenalty
                + r.finishRatePenalty;
        r.score = calculateScore(r, c, waitNorm, seatWaitNorm, queueNorm, abandonNorm);
        r.reason = buildReason(r, c);

        if (Double.isNaN(loss) || Double.isInfinite(loss)) {
            r.serviceLevelPassed = false;
            return Double.MAX_VALUE;
        }
        r.serviceLevelPassed = p95WaitMinutes <= c.hardWaitThresholdMinutes
                && p95SeatWaitMinutes <= c.hardSeatWaitThresholdMinutes
                && r.maxQueueLength <= c.hardQueueThresholdLength
                && r.abandonRate <= c.hardAbandonThresholdRate
                && r.seatUtilization <= c.hardSeatUtilizationThreshold
                && r.finishRate >= c.minAcceptFinishRate;
        return loss;
    }

    private void applyEconomics(SimRunResult r, LossConfig c) {
        double openHours = effectiveOpenHours(r);
        r.grossRevenue = r.finishedStudents * c.avgMealPrice;
        r.windowOperatingCost = r.windowCount * c.windowCostPerHour * openHours;
        r.tableOperatingCost = r.tableCount * c.tableCostPerPlan;
        r.lostOpportunityCost = r.abandonedStudents * c.lostStudentPenalty;
        r.netProfit = r.grossRevenue
                - r.windowOperatingCost
                - r.tableOperatingCost
                - r.lostOpportunityCost;
    }

    private double effectiveOpenHours(SimRunResult r) {
        double periodHours = Math.max(0.0, r.openDuration / 60.0);
        return "fullDay".equalsIgnoreCase(r.simulationModeCode) ? periodHours * 3.0 : periodHours;
    }

    private double calculateScore(SimRunResult r,
                                  LossConfig c,
                                  double waitNorm,
                                  double seatWaitNorm,
                                  double queueNorm,
                                  double abandonNorm) {
        double maxPossibleRevenue = Math.max(1.0, r.totalStudents * c.avgMealPrice);
        double profitScore = clamp((r.netProfit / maxPossibleRevenue + 1.0) / 2.0, 0.0, 1.0);
        double waitPenalty = clamp((waitNorm + seatWaitNorm + queueNorm) / 3.0, 0.0, 2.0);
        double abandonPenalty = clamp(abandonNorm, 0.0, 2.0);

        double profitWeight;
        double completionWeight;
        double waitWeight;
        double abandonWeight;
        OptimizationMode mode = c.optimizationMode;
        if (mode == OptimizationMode.PROFIT_FIRST) {
            profitWeight = 0.60;
            completionWeight = 0.22;
            waitWeight = 0.08;
            abandonWeight = 0.10;
        } else if (mode == OptimizationMode.COMPLETION_FIRST) {
            profitWeight = 0.16;
            completionWeight = 0.55;
            waitWeight = 0.14;
            abandonWeight = 0.15;
        } else if (mode == OptimizationMode.EXPERIENCE_FIRST) {
            profitWeight = 0.18;
            completionWeight = 0.24;
            waitWeight = 0.42;
            abandonWeight = 0.16;
        } else {
            profitWeight = 0.36;
            completionWeight = 0.36;
            waitWeight = 0.12;
            abandonWeight = 0.16;
        }

        return profitWeight * profitScore
                + completionWeight * r.finishRate
                - waitWeight * waitPenalty
                - abandonWeight * abandonPenalty;
    }

    private String buildReason(SimRunResult r, LossConfig c) {
        return String.format(Locale.US,
                "%s：完成率 %.1f%%，净收益 %.2f 元，平均排队 %.2f 分钟，平均等座 %.2f 分钟",
                c.optimizationMode.getDisplayName(),
                r.finishRate * 100.0,
                r.netProfit,
                r.avgWaitTimeMinutes,
                r.avgSeatWaitTimeMinutes
        );
    }

    private double calculateHardConstraintPenalty(SimRunResult r,
                                                  LossConfig c,
                                                  double p95WaitMinutes,
                                                  double p95SeatWaitMinutes) {
        double penalty = 0.0;
        penalty += c.hardWaitPenaltyWeight * quadraticOverThreshold(
                p95WaitMinutes,
                c.hardWaitThresholdMinutes,
                c.waitPenaltyScaleMinutes,
                c.hardWaitMaxPenalty
        );
        penalty += c.hardSeatWaitPenaltyWeight * quadraticOverThreshold(
                p95SeatWaitMinutes,
                c.hardSeatWaitThresholdMinutes,
                c.seatWaitPenaltyScaleMinutes,
                c.hardSeatWaitMaxPenalty
        );
        penalty += c.hardQueuePenaltyWeight * quadraticOverThreshold(
                r.maxQueueLength,
                c.hardQueueThresholdLength,
                c.queuePenaltyScaleLength,
                c.hardQueueMaxPenalty
        );
        penalty += c.hardAbandonPenaltyWeight * quadraticOverThreshold(
                r.abandonRate,
                c.hardAbandonThresholdRate,
                c.abandonPenaltyScaleRate,
                c.hardAbandonMaxPenalty
        );
        penalty += c.hardSeatUtilizationPenaltyWeight * quadraticOverThreshold(
                r.seatUtilization,
                c.hardSeatUtilizationThreshold,
                c.utilizationPenaltyScale,
                c.hardSeatUtilizationMaxPenalty
        );
        return penalty;
    }

    private double overThreshold(double value, double threshold) {
        return Math.max(0.0, value - threshold);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double quadraticOverThreshold(double value, double threshold, double scale, double maxPenalty) {
        if (value <= threshold) {
            return 0.0;
        }
        double excess = (value - threshold) / scale;
        return Math.min(maxPenalty, excess * excess);
    }

}
