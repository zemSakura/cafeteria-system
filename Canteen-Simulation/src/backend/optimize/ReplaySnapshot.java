package backend.optimize;

import java.util.Arrays;

public class ReplaySnapshot {
    public int timeSecond;
    public double timeMinute;
    public int totalQueueLength;
    public int[] windowQueueLengths;
    public int busyWindowCount;
    public int totalWindowCount;
    public int occupiedSeats;
    public int totalSeats;
    public int emptySeats;
    public int diningStudents;
    public int arrivedStudents;
    public int servedStudents;
    public int finishedStudents;
    public int abandonedStudents;

    public ReplaySnapshot copy() {
        ReplaySnapshot s = new ReplaySnapshot();
        s.timeSecond = this.timeSecond;
        s.timeMinute = this.timeMinute;
        s.totalQueueLength = this.totalQueueLength;
        s.windowQueueLengths = this.windowQueueLengths == null
                ? null
                : Arrays.copyOf(this.windowQueueLengths, this.windowQueueLengths.length);
        s.busyWindowCount = this.busyWindowCount;
        s.totalWindowCount = this.totalWindowCount;
        s.occupiedSeats = this.occupiedSeats;
        s.totalSeats = this.totalSeats;
        s.emptySeats = this.emptySeats;
        s.diningStudents = this.diningStudents;
        s.arrivedStudents = this.arrivedStudents;
        s.servedStudents = this.servedStudents;
        s.finishedStudents = this.finishedStudents;
        s.abandonedStudents = this.abandonedStudents;
        return s;
    }
}
