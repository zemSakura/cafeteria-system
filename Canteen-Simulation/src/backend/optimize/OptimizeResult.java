package backend.optimize;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class OptimizeResult {
    public SimRunResult bestResult;
    public double bestLoss = Double.MAX_VALUE;

    /**
     * Frontend-ready full process data:
     * best card uses bestResult; loss/current-best curves and window-table heatmaps use allResults.
     */
    public List<SimRunResult> allResults = new ArrayList<>();
    public List<SimRunResult> topKResults = new ArrayList<>();
    public ReplayResult replayResult;
    public long totalRuntimeMs;
    public int totalCandidateCount;
    public int totalSimulationCount;

    public void buildTopK(int k) {
        topKResults = allResults.stream()
                .sorted(Comparator.comparingDouble(r -> r.loss))
                .limit(k)
                .map(SimRunResult::copyBasic)
                .collect(Collectors.toList());
    }
}
