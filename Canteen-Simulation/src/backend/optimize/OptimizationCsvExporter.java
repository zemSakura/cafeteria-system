package backend.optimize;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

public class OptimizationCsvExporter {
    public void exportAll(OptimizeResult result, String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("创建优化输出目录失败: " + dirPath);
        }

        exportOptimizationResult(result, new File(dir, "optimization_result.csv").getPath());
        exportTopK(result, new File(dir, "top_k_result.csv").getPath());
        if (result.replayResult != null) {
            exportReplay(result.replayResult, new File(dir, "best_replay.csv").getPath());
        }
    }

    private void exportOptimizationResult(OptimizeResult result, String path) {
        try (PrintWriter out = new PrintWriter(new FileWriter(path))) {
            out.println("step,windowCount,tableCount,totalStudents,avgWaitTimeMinutes,maxQueueLength,avgQueueLength,seatUtilization,windowUtilization,finishRate,abandonRate,loss,currentBestLoss,runtimeMs");
            for (SimRunResult r : result.allResults) {
                out.println(r.step + ","
                        + r.windowCount + ","
                        + r.tableCount + ","
                        + r.totalStudents + ","
                        + r.avgWaitTimeMinutes + ","
                        + r.maxQueueLength + ","
                        + r.avgQueueLength + ","
                        + r.seatUtilization + ","
                        + r.windowUtilization + ","
                        + r.finishRate + ","
                        + r.abandonRate + ","
                        + r.loss + ","
                        + r.currentBestLoss + ","
                        + r.runtimeMs);
            }
        } catch (IOException e) {
            throw new RuntimeException("导出 optimization_result.csv 失败", e);
        }
    }

    private void exportTopK(OptimizeResult result, String path) {
        try (PrintWriter out = new PrintWriter(new FileWriter(path))) {
            out.println("rank,windowCount,tableCount,avgWaitTimeMinutes,maxQueueLength,seatUtilization,windowUtilization,finishRate,abandonRate,loss");
            int rank = 1;
            for (SimRunResult r : result.topKResults) {
                out.println(rank++ + ","
                        + r.windowCount + ","
                        + r.tableCount + ","
                        + r.avgWaitTimeMinutes + ","
                        + r.maxQueueLength + ","
                        + r.seatUtilization + ","
                        + r.windowUtilization + ","
                        + r.finishRate + ","
                        + r.abandonRate + ","
                        + r.loss);
            }
        } catch (IOException e) {
            throw new RuntimeException("导出 top_k_result.csv 失败", e);
        }
    }

    private void exportReplay(ReplayResult replay, String path) {
        try (PrintWriter out = new PrintWriter(new FileWriter(path))) {
            out.println("timeSecond,timeMinute,totalQueueLength,windowQueueLengths,busyWindowCount,totalWindowCount,occupiedSeats,totalSeats,emptySeats,diningStudents,arrivedStudents,servedStudents,finishedStudents,abandonedStudents");
            for (ReplaySnapshot s : replay.snapshots) {
                out.println(s.timeSecond + ","
                        + s.timeMinute + ","
                        + s.totalQueueLength + ","
                        + quote(Arrays.toString(s.windowQueueLengths)) + ","
                        + s.busyWindowCount + ","
                        + s.totalWindowCount + ","
                        + s.occupiedSeats + ","
                        + s.totalSeats + ","
                        + s.emptySeats + ","
                        + s.diningStudents + ","
                        + s.arrivedStudents + ","
                        + s.servedStudents + ","
                        + s.finishedStudents + ","
                        + s.abandonedStudents);
            }
        } catch (IOException e) {
            throw new RuntimeException("导出 best_replay.csv 失败", e);
        }
    }

    private String quote(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }
}
