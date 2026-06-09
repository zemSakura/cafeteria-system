package backend.dto;

/**
 * Aggregated state for one service window.
 */
public class WindowStat {
    public int windowId;
    public String type;
    public int queueLength;
    public boolean serving;
    public double avgWaitMinutes;
    public int servedCount;
    public double utilizationRate;
    public PressureLevel pressureLevel = PressureLevel.LOW;
}
