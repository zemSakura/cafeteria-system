package backend.dto;

/**
 * Improvements of the recommended plan relative to the user-entered plan.
 */
public class ComparisonResult {
    public double completionRateDelta;
    public double netProfitDelta;
    public double avgQueueWaitDelta;
    public double avgSeatWaitDelta;
    public int abandonedCountDelta;
    public double scoreDelta;
    public String summary = "";
}
