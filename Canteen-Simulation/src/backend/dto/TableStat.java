package backend.dto;

/**
 * Aggregated state for one dining table.
 */
public class TableStat {
    public int tableId;
    public int capacity;
    public int occupiedSeats;
    public TableStatus status = TableStatus.EMPTY;
    public long expectedReleaseTime = -1L;
}
