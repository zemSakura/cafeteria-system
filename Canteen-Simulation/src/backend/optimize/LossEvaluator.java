package backend.optimize;

public class LossEvaluator {
    public double evaluate(SimRunResult r, LossConfig c) {
        c.validate();
        double rawCost = r.windowCount * c.windowCost + r.tableCount * c.tableCost;
        double costNorm = rawCost / c.maxAcceptCost;
        double waitNorm = r.avgWaitTimeMinutes / c.maxAcceptWaitMinutes;
        double queueNorm = r.maxQueueLength / c.maxAcceptQueueLength;
        double abandonNorm = r.abandonRate / c.maxAcceptAbandonRate;
        double utilNorm = Math.abs(r.seatUtilization - c.targetSeatUtilization) / c.targetSeatUtilization;

        double experienceLoss = c.waitWeight * waitNorm
                + c.queueWeight * queueNorm
                + c.abandonWeight * abandonNorm
                + c.utilizationWeight * utilNorm;

        double loss = c.costWeight * costNorm
                + c.experienceWeight * experienceLoss
                + calculateHardConstraintPenalty(r, c);

        if (r.finishRate < c.minAcceptFinishRate) {
            loss += c.lowFinishRatePenalty * (c.minAcceptFinishRate - r.finishRate);
        }
        if (Double.isNaN(loss) || Double.isInfinite(loss)) {
            return Double.MAX_VALUE;
        }
        return loss;
    }

    private double calculateHardConstraintPenalty(SimRunResult r, LossConfig c) {
        double penalty = 0.0;
        penalty += c.waitExponentialPenaltyWeight * exponentialOverThreshold(
                r.avgWaitTimeMinutes,
                c.hardWaitThresholdMinutes,
                c.waitPenaltyScaleMinutes,
                c.maxExponentialPenaltyInput
        );
        penalty += c.queueExponentialPenaltyWeight * exponentialOverThreshold(
                r.maxQueueLength,
                c.hardQueueThresholdLength,
                c.queuePenaltyScaleLength,
                c.maxExponentialPenaltyInput
        );
        return penalty;
    }

    private double exponentialOverThreshold(double value, double threshold, double scale, double maxInput) {
        if (value <= threshold) {
            return 0.0;
        }
        double input = Math.min(maxInput, (value - threshold) / scale);
        return Math.expm1(input);
    }
}
