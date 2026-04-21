package backend.model;

/**
 * Simulation event.
 *
 * Arrival events now also carry mealPeriod, crowdType and arrivalSource so the
 * same event list can be used by queueing modules and frontend visualization.
 */
public class SimulationEvent implements Comparable<SimulationEvent> {

    private long eventTime;
    private EventType eventType;
    private int groupId;
    private int studentId;
    private int windowId;
    private int tableId;
    private int priority;
    private MealPeriod mealPeriod;
    private CrowdType crowdType;
    private String arrivalSource;

    public SimulationEvent(long eventTime,
                           EventType eventType,
                           int groupId,
                           int studentId,
                           int windowId,
                           int tableId,
                           int priority) {
        this(eventTime, eventType, groupId, studentId, windowId, tableId, priority,
                null, null, "");
    }

    public SimulationEvent(long eventTime,
                           EventType eventType,
                           int groupId,
                           int studentId,
                           int windowId,
                           int tableId,
                           int priority,
                           MealPeriod mealPeriod,
                           CrowdType crowdType,
                           String arrivalSource) {
        this.eventTime = eventTime;
        this.eventType = eventType;
        this.groupId = groupId;
        this.studentId = studentId;
        this.windowId = windowId;
        this.tableId = tableId;
        this.priority = priority;
        this.mealPeriod = mealPeriod;
        this.crowdType = crowdType;
        this.arrivalSource = arrivalSource == null ? "" : arrivalSource;
    }

    public long getEventTime() { return eventTime; }
    public EventType getEventType() { return eventType; }
    public int getGroupId() { return groupId; }
    public int getStudentId() { return studentId; }
    public int getWindowId() { return windowId; }
    public int getTableId() { return tableId; }
    public int getPriority() { return priority; }
    public MealPeriod getMealPeriod() { return mealPeriod; }
    public CrowdType getCrowdType() { return crowdType; }
    public String getArrivalSource() { return arrivalSource; }

    @Override
    public int compareTo(SimulationEvent other) {
        if (this.eventTime != other.eventTime) {
            return Long.compare(this.eventTime, other.eventTime);
        }
        return Integer.compare(this.priority, other.priority);
    }

    @Override
    public String toString() {
        return "SimulationEvent{" +
                "eventTime=" + eventTime +
                ", eventType=" + eventType +
                ", groupId=" + groupId +
                ", studentId=" + studentId +
                ", windowId=" + windowId +
                ", tableId=" + tableId +
                ", priority=" + priority +
                ", mealPeriod=" + mealPeriod +
                ", crowdType=" + crowdType +
                ", arrivalSource='" + arrivalSource + '\'' +
                '}';
    }
}
