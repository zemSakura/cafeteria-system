package model;

/**
 * 桌位模型
 */
public class Table {
    private int id;
    private int capacity;
    private boolean occupied;
    private int occupiedGroupId;
    private long occupiedFrom;
    private long lastReleasedTime;

    public Table(int id, int capacity) {
        this.id = id;
        this.capacity = capacity;
        this.occupied = false;
        this.occupiedGroupId = -1;
        this.occupiedFrom = -1;
        this.lastReleasedTime = -1;
    }

    public int getId() { return id; }
    public int getCapacity() { return capacity; }
    public boolean isOccupied() { return occupied; }
    public int getOccupiedGroupId() { return occupiedGroupId; }
    public long getOccupiedFrom() { return occupiedFrom; }
    public long getLastReleasedTime() { return lastReleasedTime; }

    public void occupy(int groupId, long time) {
        this.occupied = true;
        this.occupiedGroupId = groupId;
        this.occupiedFrom = time;
    }

    public void release(long time) {
        this.occupied = false;
        this.occupiedGroupId = -1;
        this.lastReleasedTime = time;
        this.occupiedFrom = -1;
    }

    @Override
    public String toString() {
        return "Table{" +
                "id=" + id +
                ", capacity=" + capacity +
                ", occupied=" + occupied +
                ", occupiedGroupId=" + occupiedGroupId +
                ", occupiedFrom=" + occupiedFrom +
                ", lastReleasedTime=" + lastReleasedTime +
                '}';
    }
}