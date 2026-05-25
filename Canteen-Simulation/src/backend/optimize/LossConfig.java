package backend.optimize;

public class LossConfig {
    public double costWeight = 0.25;
    public double waitWeight = 0.30;
    public double queueWeight = 0.20;
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

    public void validate() {
        double sum = costWeight + waitWeight + queueWeight + abandonWeight + utilizationWeight;
        if (Math.abs(sum - 1.0) > 0.0001 && sum > 0.0) {
            costWeight /= sum;
            waitWeight /= sum;
            queueWeight /= sum;
            abandonWeight /= sum;
            utilizationWeight /= sum;
        }
        if (maxAcceptCost <= 0) maxAcceptCost = 20.0;
        if (maxAcceptWaitMinutes <= 0) maxAcceptWaitMinutes = 12.0;
        if (maxAcceptQueueLength <= 0) maxAcceptQueueLength = 80.0;
        if (maxAcceptAbandonRate <= 0) maxAcceptAbandonRate = 0.20;
        if (targetSeatUtilization <= 0 || targetSeatUtilization > 1) targetSeatUtilization = 0.80;
    }
}
