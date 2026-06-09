package backend.model;

import backend.config.CanteenConfig;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Student entity used by arrival, queueing, dining and leaving modules.
 */
public class Student {

    // Data initialized by the arrival module.
    private int id;
    private int groupId;
    private int groupSize = 1;
    private long arrivalTime;
    private int diningTime;
    private int preferredWindow;
    private int patience;
    private StudentStatus status;

    // Arrival metadata for visualization and later queue analysis.
    private MealPeriod mealPeriod;
    private CrowdType crowdType;
    private String arrivalSource;

    // Data filled by later modules.
    private int finalWindowId = -1;
    private long serviceStartTime = -1;
    private long serviceEndTime = -1;
    private long seatAssignedTime = -1;
    private long diningStartTime = -1;
    private long diningEndTime = -1;
    private long leaveTime = -1;
    private int tableId = -1;
    private String leaveReason = "";
    private long queueEnterTime = -1;
    private long queueLeaveTime = -1;

    public Student(int id, int groupId, long arrivalTime) {
        this.id = id;
        this.groupId = groupId;
        this.arrivalTime = arrivalTime;
        this.status = StudentStatus.ARRIVING;
        this.mealPeriod = CanteenConfig.MEAL_PERIOD;
        this.crowdType = CrowdType.NORMAL;
        this.arrivalSource = "legacy";

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        double gaussian = rand.nextGaussian();
        this.diningTime = (int) Math.round(
                CanteenConfig.DINING_TIME_MEAN + gaussian * CanteenConfig.DINING_TIME_STD
        );
        if (this.diningTime < CanteenConfig.MIN_DINING_TIME) {
            this.diningTime = CanteenConfig.MIN_DINING_TIME;
        }

        this.preferredWindow = rand.nextInt(CanteenConfig.getWindowCount());
        this.patience = rand.nextInt(CanteenConfig.PATIENCE_MIN, CanteenConfig.PATIENCE_MAX + 1);
    }

    public Student(int id, int groupId, long arrivalTime, int diningTime, int preferredWindow, int patience) {
        this(id, groupId, arrivalTime, diningTime, preferredWindow, patience,
                CanteenConfig.MEAL_PERIOD, CrowdType.NORMAL, "legacy");
    }

    public Student(int id,
                   int groupId,
                   long arrivalTime,
                   int diningTime,
                   int preferredWindow,
                   int patience,
                   MealPeriod mealPeriod,
                   CrowdType crowdType,
                   String arrivalSource) {
        this.id = id;
        this.groupId = groupId;
        this.arrivalTime = arrivalTime;
        this.diningTime = Math.max(diningTime, CanteenConfig.MIN_DINING_TIME);
        this.preferredWindow = preferredWindow;
        this.patience = patience;
        this.status = StudentStatus.ARRIVING;
        this.mealPeriod = mealPeriod == null ? CanteenConfig.MEAL_PERIOD : mealPeriod;
        this.crowdType = crowdType == null ? CrowdType.NORMAL : crowdType;
        this.arrivalSource = arrivalSource == null ? "" : arrivalSource;
    }

    public int getId() { return id; }
    public int getGroupId() { return groupId; }
    public int getGroupSize() { return groupSize; }
    public long getArrivalTime() { return arrivalTime; }
    public int getDiningTime() { return diningTime; }
    public int getPreferredWindow() { return preferredWindow; }
    public int getPatience() { return patience; }
    public StudentStatus getStatus() { return status; }
    public MealPeriod getMealPeriod() { return mealPeriod; }
    public CrowdType getCrowdType() { return crowdType; }
    public String getArrivalSource() { return arrivalSource; }
    public int getFinalWindowId() { return finalWindowId; }
    public long getServiceStartTime() { return serviceStartTime; }
    public long getServiceEndTime() { return serviceEndTime; }
    public long getSeatAssignedTime() { return seatAssignedTime; }
    public long getDiningStartTime() { return diningStartTime; }
    public long getDiningEndTime() { return diningEndTime; }
    public long getLeaveTime() { return leaveTime; }
    public int getTableId() { return tableId; }
    public String getLeaveReason() { return leaveReason; }
    public long getQueueEnterTime() { return queueEnterTime; }
    public long getQueueLeaveTime() { return queueLeaveTime; }

    public void setStatus(StudentStatus status) { this.status = status; }
    public void setGroupSize(int groupSize) { this.groupSize = Math.max(1, Math.min(4, groupSize)); }
    public void setFinalWindowId(int finalWindowId) { this.finalWindowId = finalWindowId; }
    public void setServiceStartTime(long serviceStartTime) { this.serviceStartTime = serviceStartTime; }
    public void setServiceEndTime(long serviceEndTime) { this.serviceEndTime = serviceEndTime; }
    public void setSeatAssignedTime(long seatAssignedTime) { this.seatAssignedTime = seatAssignedTime; }
    public void setDiningStartTime(long diningStartTime) { this.diningStartTime = diningStartTime; }
    public void setDiningEndTime(long diningEndTime) { this.diningEndTime = diningEndTime; }
    public void setLeaveTime(long leaveTime) { this.leaveTime = leaveTime; }
    public void setTableId(int tableId) { this.tableId = tableId; }
    public void setLeaveReason(String leaveReason) { this.leaveReason = leaveReason; }
    public void setQueueEnterTime(long queueEnterTime) { this.queueEnterTime = queueEnterTime; }
    public void setQueueLeaveTime(long queueLeaveTime) { this.queueLeaveTime = queueLeaveTime; }
    public void setMealPeriod(MealPeriod mealPeriod) { this.mealPeriod = mealPeriod; }
    public void setCrowdType(CrowdType crowdType) { this.crowdType = crowdType; }
    public void setArrivalSource(String arrivalSource) { this.arrivalSource = arrivalSource; }

    public long getWaitTime() {
        if (serviceStartTime < 0) {
            return -1;
        }
        return serviceStartTime - arrivalTime;
    }

    public long getQueueWaitSeconds() {
        if (queueEnterTime < 0) {
            return 0L;
        }
        long end = serviceStartTime >= 0 ? serviceStartTime : queueLeaveTime;
        return end < 0 ? 0L : Math.max(0L, end - queueEnterTime);
    }

    public long getSeatWaitSeconds() {
        if (serviceEndTime < 0) {
            return 0L;
        }
        long end = seatAssignedTime >= 0 ? seatAssignedTime : leaveTime;
        return end < 0 ? 0L : Math.max(0L, end - serviceEndTime);
    }

    public long getTotalStaySeconds() {
        return getSystemStayTime();
    }

    public long getSystemStayTime() {
        if (leaveTime < 0) {
            return -1;
        }
        return leaveTime - arrivalTime;
    }

    public long getDiningDuration() {
        if (diningStartTime < 0 || diningEndTime < 0) {
            return -1;
        }
        return diningEndTime - diningStartTime;
    }

    @Override
    public String toString() {
        return String.format(
                "Student[id=%d, group=%d, status=%s, arrival=%d, meal=%s, crowd=%s, source=%s, preferredWindow=%d]",
                id, groupId, status, arrivalTime, mealPeriod, crowdType, arrivalSource, preferredWindow
        );
    }
}
