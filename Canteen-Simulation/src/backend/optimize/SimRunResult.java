package backend.optimize;

public class SimRunResult {
    public int step;
    public int windowCount;
    public int tableCount;
    public int totalStudents;
    public int arrivedStudents;
    public int servedStudents;
    public int abandonedStudents;
    public int finishedStudents;
    public double avgWaitTimeSeconds;
    public double avgWaitTimeMinutes;
    public int maxQueueLength;
    public double avgQueueLength;
    public double seatUtilization;
    public double windowUtilization;
    public double finishRate;
    public double abandonRate;
    public double loss;
    public double currentBestLoss;
    public long runtimeMs;
    public long randomSeed;
    public String mealMode;

    public SimRunResult copyBasic() {
        SimRunResult r = new SimRunResult();
        r.step = this.step;
        r.windowCount = this.windowCount;
        r.tableCount = this.tableCount;
        r.totalStudents = this.totalStudents;
        r.arrivedStudents = this.arrivedStudents;
        r.servedStudents = this.servedStudents;
        r.abandonedStudents = this.abandonedStudents;
        r.finishedStudents = this.finishedStudents;
        r.avgWaitTimeSeconds = this.avgWaitTimeSeconds;
        r.avgWaitTimeMinutes = this.avgWaitTimeMinutes;
        r.maxQueueLength = this.maxQueueLength;
        r.avgQueueLength = this.avgQueueLength;
        r.seatUtilization = this.seatUtilization;
        r.windowUtilization = this.windowUtilization;
        r.finishRate = this.finishRate;
        r.abandonRate = this.abandonRate;
        r.loss = this.loss;
        r.currentBestLoss = this.currentBestLoss;
        r.runtimeMs = this.runtimeMs;
        r.randomSeed = this.randomSeed;
        r.mealMode = this.mealMode;
        return r;
    }
}
