package model;

/**
 * 仿真事件类型
 */
public enum EventType {
    ARRIVAL,
    JOIN_QUEUE,
    SERVICE_START,
    SERVICE_END,
    SEAT_ASSIGNED,
    DINING_START,
    DINING_END,
    LEAVE,
    BALKED,
    SNAPSHOT
}