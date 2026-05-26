package backend.optimize;

public class ResultAverager {
    private int count = 0;
    private int windowCount;
    private int tableCount;
    private int totalStudents;
    private int requestedPopulation;
    private int openDuration;
    private double probSolo;
    private String simulationModeCode;
    private String mealPeriodCode;
    private int minWindowCount;
    private int maxWindowCount;
    private int minTableCount;
    private int maxTableCount;
    private long baseRandomSeed;
    private int repeatTimes;
    private double sumArrived;
    private double sumServed;
    private double sumAbandoned;
    private double sumFinished;
    private double sumAvgWaitSeconds;
    private double sumAvgWaitMinutes;
    private double sumMaxQueueLength;
    private double sumAvgQueueLength;
    private double sumSeatUtilization;
    private double sumWindowUtilization;
    private double sumFinishRate;
    private double sumAbandonRate;
    private double sumRuntimeMs;

    public void add(SimRunResult r) {
        if (count == 0) {
            windowCount = r.windowCount;
            tableCount = r.tableCount;
            totalStudents = r.totalStudents;
            requestedPopulation = r.requestedPopulation;
            openDuration = r.openDuration;
            probSolo = r.probSolo;
            simulationModeCode = r.simulationModeCode;
            mealPeriodCode = r.mealPeriodCode;
            minWindowCount = r.minWindowCount;
            maxWindowCount = r.maxWindowCount;
            minTableCount = r.minTableCount;
            maxTableCount = r.maxTableCount;
            baseRandomSeed = r.baseRandomSeed;
            repeatTimes = r.repeatTimes;
        }
        count++;
        sumArrived += r.arrivedStudents;
        sumServed += r.servedStudents;
        sumAbandoned += r.abandonedStudents;
        sumFinished += r.finishedStudents;
        sumAvgWaitSeconds += r.avgWaitTimeSeconds;
        sumAvgWaitMinutes += r.avgWaitTimeMinutes;
        sumMaxQueueLength += r.maxQueueLength;
        sumAvgQueueLength += r.avgQueueLength;
        sumSeatUtilization += r.seatUtilization;
        sumWindowUtilization += r.windowUtilization;
        sumFinishRate += r.finishRate;
        sumAbandonRate += r.abandonRate;
        sumRuntimeMs += r.runtimeMs;
    }

    public SimRunResult average() {
        if (count == 0) {
            throw new IllegalStateException("没有可平均的仿真结果");
        }
        SimRunResult r = new SimRunResult();
        r.windowCount = windowCount;
        r.tableCount = tableCount;
        r.totalStudents = totalStudents;
        r.requestedPopulation = requestedPopulation;
        r.openDuration = openDuration;
        r.probSolo = probSolo;
        r.simulationModeCode = simulationModeCode;
        r.mealPeriodCode = mealPeriodCode;
        r.minWindowCount = minWindowCount;
        r.maxWindowCount = maxWindowCount;
        r.minTableCount = minTableCount;
        r.maxTableCount = maxTableCount;
        r.baseRandomSeed = baseRandomSeed;
        r.repeatTimes = repeatTimes;
        r.arrivedStudents = (int) Math.round(sumArrived / count);
        r.servedStudents = (int) Math.round(sumServed / count);
        r.abandonedStudents = (int) Math.round(sumAbandoned / count);
        r.finishedStudents = (int) Math.round(sumFinished / count);
        r.avgWaitTimeSeconds = sumAvgWaitSeconds / count;
        r.avgWaitTimeMinutes = sumAvgWaitMinutes / count;
        r.maxQueueLength = (int) Math.round(sumMaxQueueLength / count);
        r.avgQueueLength = sumAvgQueueLength / count;
        r.seatUtilization = sumSeatUtilization / count;
        r.windowUtilization = sumWindowUtilization / count;
        r.finishRate = sumFinishRate / count;
        r.abandonRate = sumAbandonRate / count;
        r.runtimeMs = Math.round(sumRuntimeMs / count);
        return r;
    }
}
