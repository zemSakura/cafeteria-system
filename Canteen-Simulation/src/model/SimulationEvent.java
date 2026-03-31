package model;

/**
 * 仿真事件
 * 后续如果你们要接统一事件驱动引擎，可以直接使用
 */
public class SimulationEvent implements Comparable<SimulationEvent> {

    private long eventTime;
    private EventType eventType;
    private int groupId;
    private int studentId;
    private int windowId;
    private int tableId;
    private int priority;

    public SimulationEvent(long eventTime,
                           EventType eventType,
                           int groupId,
                           int studentId,
                           int windowId,
                           int tableId,
                           int priority) {
        this.eventTime = eventTime;
        this.eventType = eventType;
        this.groupId = groupId;
        this.studentId = studentId;
        this.windowId = windowId;
        this.tableId = tableId;
        this.priority = priority;
    }

    public long getEventTime() { return eventTime; }
    public EventType getEventType() { return eventType; }
    public int getGroupId() { return groupId; }
    public int getStudentId() { return studentId; }
    public int getWindowId() { return windowId; }
    public int getTableId() { return tableId; }
    public int getPriority() { return priority; }

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
                '}';
    }
}