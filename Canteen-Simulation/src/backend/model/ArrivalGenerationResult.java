package backend.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Complete output of the arrival initialization module.
 */
public class ArrivalGenerationResult {
    private final SimulationMode simulationMode;
    private final int totalPopulation;
    private final List<Student> students;
    private final List<SimulationEvent> arrivalEvents;
    private final Map<MealPeriod, MealArrivalStats> mealStats;

    /**
     * 仿真阶段边界描述
     */
    public static class PhaseBoundary {
        public final String name;       // 简短名称：早餐 / 午餐 / 晚餐 / 关闭中
        public final String label;      // 完整标签：早餐时段 06:30-09:00
        public final long startTick;    // 起始 tick（含）
        public final long endTick;      // 结束 tick（不含），-1 表示直到仿真结束

        public PhaseBoundary(String name, String label, long startTick, long endTick) {
            this.name = name;
            this.label = label;
            this.startTick = startTick;
            this.endTick = endTick;
        }
    }

    private final List<PhaseBoundary> phaseBoundaries;

    public ArrivalGenerationResult(SimulationMode simulationMode,
                                   int totalPopulation,
                                   List<Student> students,
                                   List<SimulationEvent> arrivalEvents,
                                   Map<MealPeriod, MealArrivalStats> mealStats,
                                   List<PhaseBoundary> phaseBoundaries) {
        this.simulationMode = simulationMode;
        this.totalPopulation = totalPopulation;
        this.students = new ArrayList<>(students);
        this.arrivalEvents = new ArrayList<>(arrivalEvents);
        this.mealStats = new LinkedHashMap<>(mealStats);
        this.phaseBoundaries = new ArrayList<>(phaseBoundaries);
    }

    public SimulationMode getSimulationMode() {
        return simulationMode;
    }

    public int getTotalPopulation() {
        return totalPopulation;
    }

    public List<Student> getStudents() {
        return Collections.unmodifiableList(students);
    }

    public List<SimulationEvent> getArrivalEvents() {
        return Collections.unmodifiableList(arrivalEvents);
    }

    public Map<MealPeriod, MealArrivalStats> getMealStats() {
        return Collections.unmodifiableMap(mealStats);
    }

    public List<PhaseBoundary> getPhaseBoundaries() {
        return Collections.unmodifiableList(phaseBoundaries);
    }
}
