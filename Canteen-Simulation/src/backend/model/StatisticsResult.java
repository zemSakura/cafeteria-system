package backend.model;

/**
 * 最终统计结果
 */
public class StatisticsResult {
    private double avgWaitTime;
    private double windowUtilization;
    private double tableUtilization;
    private int peakQueueLength;
    private int balkedCount;

    public double getAvgWaitTime() { return avgWaitTime; }
    public void setAvgWaitTime(double avgWaitTime) { this.avgWaitTime = avgWaitTime; }

    public double getWindowUtilization() { return windowUtilization; }
    public void setWindowUtilization(double windowUtilization) { this.windowUtilization = windowUtilization; }

    public double getTableUtilization() { return tableUtilization; }
    public void setTableUtilization(double tableUtilization) { this.tableUtilization = tableUtilization; }

    public int getPeakQueueLength() { return peakQueueLength; }
    public void setPeakQueueLength(int peakQueueLength) { this.peakQueueLength = peakQueueLength; }

    public int getBalkedCount() { return balkedCount; }
    public void setBalkedCount(int balkedCount) { this.balkedCount = balkedCount; }

    @Override
    public String toString() {
        return "StatisticsResult{" +
                "avgWaitTime=" + avgWaitTime +
                ", windowUtilization=" + windowUtilization +
                ", tableUtilization=" + tableUtilization +
                ", peakQueueLength=" + peakQueueLength +
                ", balkedCount=" + balkedCount +
                '}';
    }
}