package backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO returned to decision dashboards after a real optimization run.
 */
public class OptimizationResult {
    public PlanResult recommendedPlan;
    public PlanResult currentPlan;
    public List<PlanResult> topKPlans = new ArrayList<>();
    public List<HeatmapPoint> heatmapData = new ArrayList<>();
    public List<TrendPoint> trendData = new ArrayList<>();
    public ComparisonResult comparison;
}
