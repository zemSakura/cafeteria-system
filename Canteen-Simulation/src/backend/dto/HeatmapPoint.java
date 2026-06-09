package backend.dto;

/**
 * One evaluated cell in a window-count by table-count heatmap.
 */
public class HeatmapPoint {
    public int windowCount;
    public int tableCount;
    public double score;
    public double completionRate;
    public double netProfit;
}
