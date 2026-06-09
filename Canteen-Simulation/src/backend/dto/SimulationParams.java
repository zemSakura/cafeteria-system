package backend.dto;

import backend.config.CanteenConfig;

import java.time.LocalTime;

/**
 * Complete input contract shared by simulation and optimization frontends.
 */
public class SimulationParams {
    public int totalStudents = CanteenConfig.DEFAULT_TOTAL_POPULATION;
    public int durationMinutes = CanteenConfig.DEFAULT_OPEN_DURATION;
    public LocalTime startTime = LocalTime.of(11, 0);
    public int windowCount = CanteenConfig.DEFAULT_WINDOW_DISTANCES.length;
    public int tableCount = CanteenConfig.DEFAULT_TOTAL_TABLES;
    public int minWindowCount = 3;
    public int maxWindowCount = 6;
    public int minTableCount = 80;
    public int maxTableCount = 100;
    public double avgMealPrice = CanteenConfig.DEFAULT_AVG_MEAL_PRICE;
    public double windowCostPerHour = CanteenConfig.DEFAULT_WINDOW_COST_PER_HOUR;
    public double tableCost = CanteenConfig.DEFAULT_TABLE_COST;
    public double lostStudentPenalty = CanteenConfig.DEFAULT_LOST_STUDENT_PENALTY;
    public OptimizationMode optimizationMode = OptimizationMode.BALANCED;
    public long randomSeed = CanteenConfig.DEFAULT_RANDOM_SEED;

    public void validate() {
        if (totalStudents <= 0 || durationMinutes <= 0 || windowCount <= 0 || tableCount <= 0) {
            throw new IllegalArgumentException("人数、时长、窗口数和桌子数必须大于 0");
        }
        if (minWindowCount <= 0 || maxWindowCount < minWindowCount) {
            throw new IllegalArgumentException("窗口数量范围不合法");
        }
        if (minTableCount <= 0 || maxTableCount < minTableCount) {
            throw new IllegalArgumentException("桌子数量范围不合法");
        }
        if (avgMealPrice < 0.0 || windowCostPerHour < 0.0 || tableCost < 0.0 || lostStudentPenalty < 0.0) {
            throw new IllegalArgumentException("经营参数不能为负数");
        }
        if (optimizationMode == null) {
            optimizationMode = OptimizationMode.BALANCED;
        }
    }
}
