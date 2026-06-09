package backend.dto;

/**
 * Frontend-ready result for one evaluated window/table plan.
 */
public class PlanResult {
    public int rank;
    public int windowCount;
    public int tableCount;
    public double completionRate;
    public double netProfit;
    public double avgQueueWait;
    public double avgSeatWait;
    public int abandonedCount;
    public double windowUtilization;
    public double tableUtilization;
    public double score;
    public String reason = "";
}
