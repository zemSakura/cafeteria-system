package backend.model;

/**
 * 最终统计结果
 */
public class StatisticsResult {
    private int windowCount;
    private int tableCount;
    private int totalStudents;
    private int arrivedStudents;
    private int servedStudents;
    private int abandonedStudents;
    private int finishedStudents;
    private double avgWaitTime;
    private double avgWaitTimeSeconds;
    private double avgWaitTimeMinutes;
    private double windowUtilization;
    private double tableUtilization;
    private int peakQueueLength;
    private int maxQueueLength;
    private double avgQueueLength;
    private double seatUtilization;
    private double finishRate;
    private double abandonRate;
    private int balkedCount;
    private long runtimeMs;

    public double getAvgWaitTime() { return avgWaitTime; }
    public void setAvgWaitTime(double avgWaitTime) {
        this.avgWaitTime = avgWaitTime;
        this.avgWaitTimeSeconds = avgWaitTime;
        this.avgWaitTimeMinutes = avgWaitTime / 60.0;
    }

    public int getWindowCount() { return windowCount; }
    public void setWindowCount(int windowCount) { this.windowCount = windowCount; }

    public int getTableCount() { return tableCount; }
    public void setTableCount(int tableCount) { this.tableCount = tableCount; }

    public int getTotalStudents() { return totalStudents; }
    public void setTotalStudents(int totalStudents) { this.totalStudents = totalStudents; }

    public int getArrivedStudents() { return arrivedStudents; }
    public void setArrivedStudents(int arrivedStudents) { this.arrivedStudents = arrivedStudents; }

    public int getServedStudents() { return servedStudents; }
    public void setServedStudents(int servedStudents) { this.servedStudents = servedStudents; }

    public int getAbandonedStudents() { return abandonedStudents; }
    public void setAbandonedStudents(int abandonedStudents) {
        this.abandonedStudents = abandonedStudents;
        this.balkedCount = abandonedStudents;
    }

    public int getFinishedStudents() { return finishedStudents; }
    public void setFinishedStudents(int finishedStudents) { this.finishedStudents = finishedStudents; }

    public double getAvgWaitTimeSeconds() { return avgWaitTimeSeconds; }
    public void setAvgWaitTimeSeconds(double avgWaitTimeSeconds) {
        this.avgWaitTimeSeconds = avgWaitTimeSeconds;
        this.avgWaitTime = avgWaitTimeSeconds;
        this.avgWaitTimeMinutes = avgWaitTimeSeconds / 60.0;
    }

    public double getAvgWaitTimeMinutes() { return avgWaitTimeMinutes; }
    public void setAvgWaitTimeMinutes(double avgWaitTimeMinutes) { this.avgWaitTimeMinutes = avgWaitTimeMinutes; }

    public double getWindowUtilization() { return windowUtilization; }
    public void setWindowUtilization(double windowUtilization) { this.windowUtilization = windowUtilization; }

    public double getTableUtilization() { return tableUtilization; }
    public void setTableUtilization(double tableUtilization) {
        this.tableUtilization = tableUtilization;
        this.seatUtilization = tableUtilization;
    }

    public int getPeakQueueLength() { return peakQueueLength; }
    public void setPeakQueueLength(int peakQueueLength) {
        this.peakQueueLength = peakQueueLength;
        this.maxQueueLength = peakQueueLength;
    }

    public int getMaxQueueLength() { return maxQueueLength; }
    public void setMaxQueueLength(int maxQueueLength) {
        this.maxQueueLength = maxQueueLength;
        this.peakQueueLength = maxQueueLength;
    }

    public double getAvgQueueLength() { return avgQueueLength; }
    public void setAvgQueueLength(double avgQueueLength) { this.avgQueueLength = avgQueueLength; }

    public double getSeatUtilization() { return seatUtilization; }
    public void setSeatUtilization(double seatUtilization) {
        this.seatUtilization = seatUtilization;
        this.tableUtilization = seatUtilization;
    }

    public double getFinishRate() { return finishRate; }
    public void setFinishRate(double finishRate) { this.finishRate = finishRate; }

    public double getAbandonRate() { return abandonRate; }
    public void setAbandonRate(double abandonRate) { this.abandonRate = abandonRate; }

    public int getBalkedCount() { return balkedCount; }
    public void setBalkedCount(int balkedCount) {
        this.balkedCount = balkedCount;
        this.abandonedStudents = balkedCount;
    }

    public long getRuntimeMs() { return runtimeMs; }
    public void setRuntimeMs(long runtimeMs) { this.runtimeMs = runtimeMs; }

    @Override
    public String toString() {
        return "StatisticsResult{" +
                "avgWaitTime=" + avgWaitTime +
                ", avgWaitTimeMinutes=" + avgWaitTimeMinutes +
                ", windowUtilization=" + windowUtilization +
                ", tableUtilization=" + tableUtilization +
                ", peakQueueLength=" + peakQueueLength +
                ", avgQueueLength=" + avgQueueLength +
                ", finishRate=" + finishRate +
                ", abandonRate=" + abandonRate +
                ", balkedCount=" + balkedCount +
                '}';
    }
}
