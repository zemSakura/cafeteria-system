package backend.optimize;

import backend.config.CanteenConfig;
import backend.dto.OptimizationMode;

public class LossConfig {
    // Top-level weights
    public double costWeight = 0.35;
    public double experienceWeight = 1.00;

    // Experience sub-weights (auto-normalized to 1.0 in validate)
    public double waitWeight = 0.25;
    public double seatWaitWeight = 0.20;
    public double queueWeight = 0.15;
    public double abandonWeight = 0.35;
    public double crowdingWeight = 0.05;

    // Cost normalization
    public double windowCost = 1.0;
    public double tableCost = 0.08;
    public double maxAcceptCost = 20.0;

    // Service-quality normalization ceilings
    public double maxAcceptWaitMinutes = 8.0;
    public double maxAcceptQueueLength = 80.0;
    public double maxAcceptAbandonRate = 0.20;
    public double comfortableSeatUtilization = 0.85;
    public double seatCrowdingScale = 0.10;

    // Finish-rate penalty
    public double lowFinishRatePenalty = 3.0;
    public double minAcceptFinishRate = 0.95;

    // Hard SLA — P95 wait time (quadratic over threshold)
    public double hardWaitThresholdMinutes = 10.0;
    public double waitPenaltyScaleMinutes = 2.0;
    public double hardWaitPenaltyWeight = 1.0;
    public double hardWaitMaxPenalty = 50.0;

    // Hard SLA for P95 seat-wait time after service completion.
    public double hardSeatWaitThresholdMinutes = 6.0;
    public double seatWaitPenaltyScaleMinutes = 2.0;
    public double hardSeatWaitPenaltyWeight = 1.0;
    public double hardSeatWaitMaxPenalty = 40.0;

    // Hard SLA — queue length (quadratic over threshold)
    public double hardQueueThresholdLength = 50.0;
    public double queuePenaltyScaleLength = 10.0;
    public double hardQueuePenaltyWeight = 1.0;
    public double hardQueueMaxPenalty = 50.0;

    // Hard SLA — abandonment rate (quadratic over threshold)
    public double hardAbandonThresholdRate = 0.05;
    public double abandonPenaltyScaleRate = 0.02;
    public double hardAbandonPenaltyWeight = 1.0;
    public double hardAbandonMaxPenalty = 50.0;

    // Hard SLA — seat crowding ceiling (quadratic over threshold)
    public double hardSeatUtilizationThreshold = 0.95;
    public double utilizationPenaltyScale = 0.05;
    public double hardSeatUtilizationPenaltyWeight = 1.0;
    public double hardSeatUtilizationMaxPenalty = 30.0;

    // Economic and decision-mode inputs.
    public double avgMealPrice = CanteenConfig.DEFAULT_AVG_MEAL_PRICE;
    public double windowCostPerHour = CanteenConfig.DEFAULT_WINDOW_COST_PER_HOUR;
    public double tableCostPerPlan = CanteenConfig.DEFAULT_TABLE_COST;
    public double lostStudentPenalty = CanteenConfig.DEFAULT_LOST_STUDENT_PENALTY;
    public OptimizationMode optimizationMode = OptimizationMode.PROFIT_FIRST;

    public void apply(OptimizeConfig config) {
        avgMealPrice = config.avgMealPrice;
        windowCostPerHour = config.windowCostPerHour;
        tableCostPerPlan = config.tableCost;
        lostStudentPenalty = config.lostStudentPenalty;
        optimizationMode = config.optimizationMode;
    }

    public void validate() {
        if (costWeight < 0) costWeight = 0.0;
        if (experienceWeight < 0) experienceWeight = 0.0;
        if (waitWeight < 0) waitWeight = 0.0;
        if (seatWaitWeight < 0) seatWaitWeight = 0.0;
        if (queueWeight < 0) queueWeight = 0.0;
        if (abandonWeight < 0) abandonWeight = 0.0;
        if (crowdingWeight < 0) crowdingWeight = 0.0;

        double sum = waitWeight + seatWaitWeight + queueWeight + abandonWeight + crowdingWeight;
        if (Math.abs(sum - 1.0) > 0.0001 && sum > 0.0) {
            waitWeight /= sum;
            seatWaitWeight /= sum;
            queueWeight /= sum;
            abandonWeight /= sum;
            crowdingWeight /= sum;
        }
        if (sum <= 0.0) {
            waitWeight = 0.25;
            seatWaitWeight = 0.20;
            queueWeight = 0.15;
            abandonWeight = 0.35;
            crowdingWeight = 0.05;
        }

        if (maxAcceptCost <= 0) maxAcceptCost = 20.0;
        if (maxAcceptWaitMinutes <= 0) maxAcceptWaitMinutes = 8.0;
        if (maxAcceptQueueLength <= 0) maxAcceptQueueLength = 80.0;
        if (maxAcceptAbandonRate <= 0) maxAcceptAbandonRate = 0.20;
        if (comfortableSeatUtilization <= 0 || comfortableSeatUtilization >= 1) comfortableSeatUtilization = 0.85;
        if (seatCrowdingScale <= 0) seatCrowdingScale = 0.10;
        if (minAcceptFinishRate < 0.0 || minAcceptFinishRate > 1.0) minAcceptFinishRate = 0.95;
        if (lowFinishRatePenalty < 0.0) lowFinishRatePenalty = 0.0;

        if (hardWaitThresholdMinutes <= 0) hardWaitThresholdMinutes = 10.0;
        if (hardSeatWaitThresholdMinutes <= 0) hardSeatWaitThresholdMinutes = 6.0;
        if (hardQueueThresholdLength <= 0) hardQueueThresholdLength = 50.0;
        if (waitPenaltyScaleMinutes <= 0) waitPenaltyScaleMinutes = 2.0;
        if (seatWaitPenaltyScaleMinutes <= 0) seatWaitPenaltyScaleMinutes = 2.0;
        if (queuePenaltyScaleLength <= 0) queuePenaltyScaleLength = 10.0;
        if (hardWaitPenaltyWeight < 0.0) hardWaitPenaltyWeight = 0.0;
        if (hardSeatWaitPenaltyWeight < 0.0) hardSeatWaitPenaltyWeight = 0.0;
        if (hardQueuePenaltyWeight < 0.0) hardQueuePenaltyWeight = 0.0;
        if (hardWaitMaxPenalty <= 0.0) hardWaitMaxPenalty = 50.0;
        if (hardSeatWaitMaxPenalty <= 0.0) hardSeatWaitMaxPenalty = 40.0;
        if (hardQueueMaxPenalty <= 0.0) hardQueueMaxPenalty = 50.0;

        if (hardAbandonThresholdRate <= 0.0 || hardAbandonThresholdRate > 1.0) hardAbandonThresholdRate = 0.05;
        if (abandonPenaltyScaleRate <= 0.0) abandonPenaltyScaleRate = 0.02;
        if (hardAbandonPenaltyWeight < 0.0) hardAbandonPenaltyWeight = 0.0;
        if (hardAbandonMaxPenalty <= 0.0) hardAbandonMaxPenalty = 50.0;

        if (hardSeatUtilizationThreshold <= 0.0 || hardSeatUtilizationThreshold > 1.0) hardSeatUtilizationThreshold = 0.95;
        if (utilizationPenaltyScale <= 0.0) utilizationPenaltyScale = 0.05;
        if (hardSeatUtilizationPenaltyWeight < 0.0) hardSeatUtilizationPenaltyWeight = 0.0;
        if (hardSeatUtilizationMaxPenalty <= 0.0) hardSeatUtilizationMaxPenalty = 30.0;
        if (avgMealPrice < 0.0) avgMealPrice = 0.0;
        if (windowCostPerHour < 0.0) windowCostPerHour = 0.0;
        if (tableCostPerPlan < 0.0) tableCostPerPlan = 0.0;
        if (lostStudentPenalty < 0.0) lostStudentPenalty = 0.0;
        if (optimizationMode == null) optimizationMode = OptimizationMode.BALANCED;
    }
}
