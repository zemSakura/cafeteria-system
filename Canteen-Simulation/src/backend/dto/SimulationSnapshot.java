package backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only-by-convention view model emitted by the simulation engine.
 *
 * The frontend renders only these aggregate structures, so a 3000-student run
 * never needs to draw or retain a Swing component per student.
 */
public class SimulationSnapshot {
    public long currentTime;
    public int totalStudents;
    public int arrivedCount;
    public int queueingCount;
    public int servingCount;
    public int waitingSeatCount;
    public int diningCount;
    public int completedCount;
    public int abandonedCount;
    public double completionRate;
    public double abandonRate;
    public double avgQueueWaitSeconds;
    public double p95QueueWaitSeconds;
    public double avgSeatWaitSeconds;
    public double p95SeatWaitSeconds;
    public double avgTotalStaySeconds;
    public double p95TotalStaySeconds;
    public int maxQueueLength;
    public int maxWaitingSeatCount;
    public double windowUtilizationRate;
    public double tableUtilizationRate;
    public double seatUtilizationRate;
    public double grossRevenue;
    public double windowCost;
    public double tableCost;
    public double lostOpportunityCost;
    public double netProfit;
    public RenderMode renderMode = RenderMode.INDIVIDUAL;
    public List<WindowStat> windowStats = new ArrayList<>();
    public List<TableStat> tableStats = new ArrayList<>();
    public int[][] tableMatrix = new int[0][0];
    public List<TrendPoint> trendPoints = new ArrayList<>();
}
