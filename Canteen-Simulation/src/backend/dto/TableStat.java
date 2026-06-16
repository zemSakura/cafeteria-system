package backend.dto;

/**
 * Aggregated state for one dining table.
 */
public class TableStat {
    public int tableId;
    public int capacity;
    public int occupiedSeats;
    public int reservedOrOccupiedSeats;
    public int[] seatGroupIds = new int[0];
    public TableStatus status = TableStatus.EMPTY;
    public long expectedReleaseTime = -1L;
}
