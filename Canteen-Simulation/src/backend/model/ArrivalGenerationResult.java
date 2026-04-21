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

    public ArrivalGenerationResult(SimulationMode simulationMode,
                                   int totalPopulation,
                                   List<Student> students,
                                   List<SimulationEvent> arrivalEvents,
                                   Map<MealPeriod, MealArrivalStats> mealStats) {
        this.simulationMode = simulationMode;
        this.totalPopulation = totalPopulation;
        this.students = new ArrayList<>(students);
        this.arrivalEvents = new ArrayList<>(arrivalEvents);
        this.mealStats = new LinkedHashMap<>(mealStats);
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
}
