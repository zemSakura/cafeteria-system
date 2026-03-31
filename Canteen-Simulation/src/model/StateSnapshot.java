package model;

import java.util.Arrays;

/**
 * 某个时刻的系统状态快照
 */
public class StateSnapshot {
    private long time;
    private int inSystemCount;
    private int queuingCount;
    private int diningCount;
    private int arrivedTotal;
    private int leftTotal;
    private int balkedTotal;
    private int occupiedTables;
    private int freeTables;
    private int[] windowQueueLengths;
    private boolean[] windowBusyFlags;

    public StateSnapshot(long time,
                         int inSystemCount,
                         int queuingCount,
                         int diningCount,
                         int arrivedTotal,
                         int leftTotal,
                         int balkedTotal,
                         int occupiedTables,
                         int freeTables,
                         int[] windowQueueLengths,
                         boolean[] windowBusyFlags) {
        this.time = time;
        this.inSystemCount = inSystemCount;
        this.queuingCount = queuingCount;
        this.diningCount = diningCount;
        this.arrivedTotal = arrivedTotal;
        this.leftTotal = leftTotal;
        this.balkedTotal = balkedTotal;
        this.occupiedTables = occupiedTables;
        this.freeTables = freeTables;
        this.windowQueueLengths = windowQueueLengths.clone();
        this.windowBusyFlags = windowBusyFlags.clone();
    }

    public long getTime() { return time; }
    public int getInSystemCount() { return inSystemCount; }
    public int getQueuingCount() { return queuingCount; }
    public int getDiningCount() { return diningCount; }
    public int getArrivedTotal() { return arrivedTotal; }
    public int getLeftTotal() { return leftTotal; }
    public int getBalkedTotal() { return balkedTotal; }
    public int getOccupiedTables() { return occupiedTables; }
    public int getFreeTables() { return freeTables; }
    public int[] getWindowQueueLengths() { return windowQueueLengths.clone(); }
    public boolean[] getWindowBusyFlags() { return windowBusyFlags.clone(); }

    @Override
    public String toString() {
        return "StateSnapshot{" +
                "time=" + time +
                ", inSystemCount=" + inSystemCount +
                ", queuingCount=" + queuingCount +
                ", diningCount=" + diningCount +
                ", arrivedTotal=" + arrivedTotal +
                ", leftTotal=" + leftTotal +
                ", balkedTotal=" + balkedTotal +
                ", occupiedTables=" + occupiedTables +
                ", freeTables=" + freeTables +
                ", windowQueueLengths=" + Arrays.toString(windowQueueLengths) +
                ", windowBusyFlags=" + Arrays.toString(windowBusyFlags) +
                '}';
    }
}