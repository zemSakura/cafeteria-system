package backend.optimize;

public class LossConfig {
    public double costWeight = 0.15;
    public double experienceWeight = 1.00;
    public double waitWeight = 0.45;
    public double queueWeight = 0.30;
    public double abandonWeight = 0.15;
    public double utilizationWeight = 0.10;
    public double windowCost = 1.0;
    public double tableCost = 0.08;
    public double maxAcceptCost = 20.0;
    public double maxAcceptWaitMinutes = 12.0;
    public double maxAcceptQueueLength = 80.0;
    public double maxAcceptAbandonRate = 0.20;
    public double targetSeatUtilization = 0.80;
    public double lowFinishRatePenalty = 3.0;
    public double minAcceptFinishRate = 0.95;
    public double hardWaitThresholdMinutes = 3.0;
    public double hardQueueThresholdLength = 50.0;
    public double waitPenaltyScaleMinutes = 1.0;
    public double queuePenaltyScaleLength = 10.0;
    public double waitExponentialPenaltyWeight = 2.0;
    public double queueExponentialPenaltyWeight = 2.0;
    public double maxExponentialPenaltyInput = 12.0;

    public void validate() {
        if (costWeight < 0) costWeight = 0.0;
        if (experienceWeight < 0) experienceWeight = 0.0;
        if (waitWeight < 0) waitWeight = 0.0;
        if (queueWeight < 0) queueWeight = 0.0;
        if (abandonWeight < 0) abandonWeight = 0.0;
        if (utilizationWeight < 0) utilizationWeight = 0.0;

        double sum = waitWeight + queueWeight + abandonWeight + utilizationWeight;
        if (Math.abs(sum - 1.0) > 0.0001 && sum > 0.0) {
            waitWeight /= sum;
            queueWeight /= sum;
            abandonWeight /= sum;
            utilizationWeight /= sum;
        }
        if (sum <= 0.0) {
            waitWeight = 0.45;
            queueWeight = 0.30;
            abandonWeight = 0.15;
            utilizationWeight = 0.10;
        }

        if (maxAcceptCost <= 0) maxAcceptCost = 20.0;
        if (maxAcceptWaitMinutes <= 0) maxAcceptWaitMinutes = 12.0;
        if (maxAcceptQueueLength <= 0) maxAcceptQueueLength = 80.0;
        if (maxAcceptAbandonRate <= 0) maxAcceptAbandonRate = 0.20;
        if (targetSeatUtilization <= 0 || targetSeatUtilization > 1) targetSeatUtilization = 0.80;
        if (minAcceptFinishRate < 0.0 || minAcceptFinishRate > 1.0) minAcceptFinishRate = 0.95;
        if (lowFinishRatePenalty < 0.0) lowFinishRatePenalty = 0.0;
        if (hardWaitThresholdMinutes <= 0) hardWaitThresholdMinutes = 3.0;
        if (hardQueueThresholdLength <= 0) hardQueueThresholdLength = 50.0;
        if (waitPenaltyScaleMinutes <= 0) waitPenaltyScaleMinutes = 1.0;
        if (queuePenaltyScaleLength <= 0) queuePenaltyScaleLength = 10.0;
        if (waitExponentialPenaltyWeight < 0.0) waitExponentialPenaltyWeight = 0.0;
        if (queueExponentialPenaltyWeight < 0.0) queueExponentialPenaltyWeight = 0.0;
        if (maxExponentialPenaltyInput <= 0.0) maxExponentialPenaltyInput = 12.0;
    }
}
