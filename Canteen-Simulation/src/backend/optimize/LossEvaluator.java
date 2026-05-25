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

        double loss = c.costWeight * costNorm
                + c.waitWeight * waitNorm
                + c.queueWeight * queueNorm
                + c.abandonWeight * abandonNorm
                + c.utilizationWeight * utilNorm;

        if (r.finishRate < c.minAcceptFinishRate) {
            loss += c.lowFinishRatePenalty * (c.minAcceptFinishRate - r.finishRate);
        }
        if (Double.isNaN(loss) || Double.isInfinite(loss)) {
            return Double.MAX_VALUE;
        }
        return loss;
    }
}
