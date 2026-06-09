package backend.dto;

/**
 * One backend-generated point for live trend charts.
 */
public class TrendPoint {
    public long timeSecond;
    public int queueingCount;
    public int waitingSeatCount;
    public double windowUtilizationRate;
    public double tableUtilizationRate;
    public int completedCount;
    public int abandonedCount;

    public TrendPoint copy() {
        TrendPoint point = new TrendPoint();
        point.timeSecond = timeSecond;
        point.queueingCount = queueingCount;
        point.waitingSeatCount = waitingSeatCount;
        point.windowUtilizationRate = windowUtilizationRate;
        point.tableUtilizationRate = tableUtilizationRate;
        point.completedCount = completedCount;
        point.abandonedCount = abandonedCount;
        return point;
    }
}
