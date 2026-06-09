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
    private double p95WaitTimeSeconds;
    private double p95WaitTimeMinutes;
    private double avgSeatWaitTimeSeconds;
    private double avgSeatWaitTimeMinutes;
    private double p95SeatWaitTimeSeconds;
    private double p95SeatWaitTimeMinutes;
    private double avgTotalStayTimeSeconds;
    private double avgTotalStayTimeMinutes;
    private double p95TotalStayTimeSeconds;
    private double p95TotalStayTimeMinutes;
    private double windowUtilization;
    private double tableUtilization;
    private int peakQueueLength;
    private int maxQueueLength;
    private double avgQueueLength;
    private double seatUtilization;
    private double finishRate;
    private double abandonRate;
    private int balkedCount;
    private int maxWaitingSeatCount;
    private double grossRevenue;
    private double windowCost;
    private double tableCost;
    private double lostOpportunityCost;
    private double netProfit;
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

    public double getP95WaitTimeSeconds() { return p95WaitTimeSeconds; }
    public void setP95WaitTimeSeconds(double p95WaitTimeSeconds) {
        this.p95WaitTimeSeconds = p95WaitTimeSeconds;
        this.p95WaitTimeMinutes = p95WaitTimeSeconds / 60.0;
    }

    public double getP95WaitTimeMinutes() { return p95WaitTimeMinutes; }
    public void setP95WaitTimeMinutes(double p95WaitTimeMinutes) {
        this.p95WaitTimeMinutes = p95WaitTimeMinutes;
        this.p95WaitTimeSeconds = p95WaitTimeMinutes * 60.0;
    }

    public double getAvgSeatWaitTimeSeconds() { return avgSeatWaitTimeSeconds; }
    public void setAvgSeatWaitTimeSeconds(double value) {
        this.avgSeatWaitTimeSeconds = value;
        this.avgSeatWaitTimeMinutes = value / 60.0;
    }

    public double getAvgSeatWaitTimeMinutes() { return avgSeatWaitTimeMinutes; }
    public void setAvgSeatWaitTimeMinutes(double value) {
        this.avgSeatWaitTimeMinutes = value;
        this.avgSeatWaitTimeSeconds = value * 60.0;
    }

    public double getP95SeatWaitTimeSeconds() { return p95SeatWaitTimeSeconds; }
    public void setP95SeatWaitTimeSeconds(double value) {
        this.p95SeatWaitTimeSeconds = value;
        this.p95SeatWaitTimeMinutes = value / 60.0;
    }

    public double getP95SeatWaitTimeMinutes() { return p95SeatWaitTimeMinutes; }
    public void setP95SeatWaitTimeMinutes(double value) {
        this.p95SeatWaitTimeMinutes = value;
        this.p95SeatWaitTimeSeconds = value * 60.0;
    }

    public double getAvgTotalStayTimeSeconds() { return avgTotalStayTimeSeconds; }
    public void setAvgTotalStayTimeSeconds(double value) {
        this.avgTotalStayTimeSeconds = value;
        this.avgTotalStayTimeMinutes = value / 60.0;
    }

    public double getAvgTotalStayTimeMinutes() { return avgTotalStayTimeMinutes; }
    public void setAvgTotalStayTimeMinutes(double value) {
        this.avgTotalStayTimeMinutes = value;
        this.avgTotalStayTimeSeconds = value * 60.0;
    }

    public double getP95TotalStayTimeSeconds() { return p95TotalStayTimeSeconds; }
    public void setP95TotalStayTimeSeconds(double value) {
        this.p95TotalStayTimeSeconds = value;
        this.p95TotalStayTimeMinutes = value / 60.0;
    }

    public double getP95TotalStayTimeMinutes() { return p95TotalStayTimeMinutes; }
    public void setP95TotalStayTimeMinutes(double value) {
        this.p95TotalStayTimeMinutes = value;
        this.p95TotalStayTimeSeconds = value * 60.0;
    }

    public double getWindowUtilization() { return windowUtilization; }
    public void setWindowUtilization(double windowUtilization) { this.windowUtilization = windowUtilization; }

    public double getTableUtilization() { return tableUtilization; }
    public void setTableUtilization(double tableUtilization) { this.tableUtilization = tableUtilization; }

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
    public void setSeatUtilization(double seatUtilization) { this.seatUtilization = seatUtilization; }

    public double getFinishRate() { return finishRate; }
    public void setFinishRate(double finishRate) { this.finishRate = finishRate; }

    public double getAbandonRate() { return abandonRate; }
    public void setAbandonRate(double abandonRate) { this.abandonRate = abandonRate; }

    public int getBalkedCount() { return balkedCount; }
    public void setBalkedCount(int balkedCount) {
        this.balkedCount = balkedCount;
        this.abandonedStudents = balkedCount;
    }

    public int getMaxWaitingSeatCount() { return maxWaitingSeatCount; }
    public void setMaxWaitingSeatCount(int maxWaitingSeatCount) { this.maxWaitingSeatCount = maxWaitingSeatCount; }

    public double getGrossRevenue() { return grossRevenue; }
    public void setGrossRevenue(double grossRevenue) { this.grossRevenue = grossRevenue; }

    public double getWindowCost() { return windowCost; }
    public void setWindowCost(double windowCost) { this.windowCost = windowCost; }

    public double getTableCost() { return tableCost; }
    public void setTableCost(double tableCost) { this.tableCost = tableCost; }

    public double getLostOpportunityCost() { return lostOpportunityCost; }
    public void setLostOpportunityCost(double lostOpportunityCost) { this.lostOpportunityCost = lostOpportunityCost; }

    public double getNetProfit() { return netProfit; }
    public void setNetProfit(double netProfit) { this.netProfit = netProfit; }

    public long getRuntimeMs() { return runtimeMs; }
    public void setRuntimeMs(long runtimeMs) { this.runtimeMs = runtimeMs; }

    @Override
    public String toString() {
        return "StatisticsResult{" +
                "avgWaitTime=" + avgWaitTime +
                ", avgWaitTimeMinutes=" + avgWaitTimeMinutes +
                ", p95WaitTimeMinutes=" + p95WaitTimeMinutes +
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
