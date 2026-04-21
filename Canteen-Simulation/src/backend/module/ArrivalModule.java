package backend.module;

import backend.config.CanteenConfig;
import backend.model.ArrivalDistributionPoint;
import backend.model.ArrivalGenerationResult;
import backend.model.ArrivalPeak;
import backend.model.CrowdType;
import backend.model.EventType;
import backend.model.Group;
import backend.model.MealArrivalStats;
import backend.model.MealPeriod;
import backend.model.SimulationEvent;
import backend.model.SimulationMode;
import backend.model.Student;
import backend.model.Window;
import backend.model.WindowState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

/**
 * Arrival initialization module.
 *
 * Responsibilities:
 * 1. Initialize static canteen windows.
 * 2. Derive meal populations from totalPopulation.
 * 3. Generate concrete student arrivals from background flow plus Gaussian peaks.
 * 4. Provide visualization data for frontend charts.
 *
 * This module does not operate Swing UI components and does not execute queueing
 * or dining logic.
 */
public class ArrivalModule {

    private static final double SQRT_TWO_PI = Math.sqrt(2.0 * Math.PI);

    private final BlockingQueue<Student> queue;
    private long seed;
    private Random random;
    private ArrivalGenerationResult lastGenerationResult;

    public ArrivalModule() {
        this(null, CanteenConfig.RANDOM_SEED);
    }

    public ArrivalModule(long seed) {
        this(null, seed);
    }

    public ArrivalModule(BlockingQueue<Student> queue) {
        this(queue, CanteenConfig.RANDOM_SEED);
    }

    public ArrivalModule(BlockingQueue<Student> queue, long seed) {
        this.queue = queue;
        this.seed = seed;
        this.random = new Random(seed);
    }

    public void resetForNextRun() {
        this.random = new Random(this.seed);
        this.lastGenerationResult = null;
        if (this.queue != null) {
            this.queue.clear();
        }
    }

    public void resetForNextRun(long newSeed) {
        this.seed = newSeed;
        this.random = new Random(newSeed);
        this.lastGenerationResult = null;
        if (this.queue != null) {
            this.queue.clear();
        }
    }

    public long getSeed() {
        return seed;
    }

    public ArrivalGenerationResult getLastGenerationResult() {
        return lastGenerationResult;
    }

    public List<Window> initWindows() {
        List<Window> windows = new ArrayList<>();

        for (int i = 0; i < CanteenConfig.getWindowCount(); i++) {
            windows.add(new Window(
                    i,
                    CanteenConfig.WINDOW_DISTANCES[i],
                    CanteenConfig.WINDOW_AVG_SERVE_TIME[i]
            ));
        }

        return windows;
    }

    public List<WindowState> initWindowStates() {
        List<Window> windows = initWindows();
        List<WindowState> states = new ArrayList<>();

        for (Window window : windows) {
            states.add(new WindowState(window));
        }

        return states;
    }

    /**
     * Backward-compatible entry used by the existing frontend.
     */
    public List<Student> generateStudents() {
        ArrivalGenerationResult result = generateArrivalPlan(
                CanteenConfig.TOTAL_POPULATION,
                CanteenConfig.SIMULATION_MODE,
                CanteenConfig.MEAL_PERIOD
        );
        return new ArrayList<>(result.getStudents());
    }

    /**
     * New explicit entry for frontend/backend integration.
     */
    public ArrivalGenerationResult generateArrivalPlan(int totalPopulation,
                                                       SimulationMode simulationMode,
                                                       MealPeriod mealPeriod) {
        if (totalPopulation <= 0) {
            throw new IllegalArgumentException("totalPopulation must be greater than 0");
        }

        SimulationMode mode = simulationMode == null ? SimulationMode.SINGLE_PERIOD : simulationMode;
        MealPeriod selectedPeriod = mealPeriod == null ? MealPeriod.LUNCH : mealPeriod;

        Map<MealPeriod, Integer> populations = deriveMealPopulations(totalPopulation);
        List<MealPeriod> periods = resolvePeriods(mode, selectedPeriod);

        List<Student> students = new ArrayList<>();
        Map<MealPeriod, MealArrivalStats> mealStats = new LinkedHashMap<>();

        int[] counters = {1, 1};
        int simulationStartMinuteOfDay = periods.get(0).getStartMinute();

        for (MealPeriod period : periods) {
            int population = populations.get(period);
            PeriodGeneration generation = generatePeriodStudents(
                    period,
                    population,
                    simulationStartMinuteOfDay,
                    counters
            );
            students.addAll(generation.students);
            mealStats.put(period, generation.stats);
        }

        students.sort(Comparator.comparingLong(Student::getArrivalTime)
                .thenComparingInt(Student::getGroupId)
                .thenComparingInt(Student::getId));

        List<SimulationEvent> events = generateArrivalEvents(students);

        lastGenerationResult = new ArrivalGenerationResult(
                mode,
                totalPopulation,
                students,
                events,
                mealStats
        );

        if (queue != null) {
            queue.clear();
            queue.addAll(students);
        }

        return lastGenerationResult;
    }

    /**
     * First layer: derive meal populations from totalPopulation.
     *
     * General campus rule enforced here:
     * breakfast < dinner < lunch.
     */
    public Map<MealPeriod, Integer> deriveMealPopulations(int totalPopulation) {
        int breakfast = (int) Math.round(totalPopulation * (
                CanteenConfig.BREAKFAST_BASE_RATE * (1.0 - CanteenConfig.BREAKFAST_SKIP_RATE)
                        + CanteenConfig.EARLY_CLASS_RATIO * CanteenConfig.EARLY_CLASS_BREAKFAST_BOOST
        ));

        int lunch = (int) Math.round(totalPopulation
                * CanteenConfig.LUNCH_BASE_RATE
                * (1.0 - CanteenConfig.TAKEOUT_RATE_AT_LUNCH));

        int dinner = (int) Math.round(totalPopulation * (
                CanteenConfig.DINNER_BASE_RATE
                        + CanteenConfig.EVENING_CLASS_RATIO * 0.10
        ) * (1.0 - CanteenConfig.DINNER_SKIP_OR_OFF_CAMPUS_RATE));

        lunch = Math.max(lunch, 1);
        dinner = Math.max(dinner, 1);
        breakfast = Math.max(breakfast, 1);

        if (dinner >= lunch) {
            dinner = Math.max(1, (int) Math.round(lunch * 0.78));
        }
        if (breakfast >= dinner) {
            breakfast = Math.max(1, (int) Math.round(dinner * 0.62));
        }

        Map<MealPeriod, Integer> result = new LinkedHashMap<>();
        result.put(MealPeriod.BREAKFAST, breakfast);
        result.put(MealPeriod.LUNCH, lunch);
        result.put(MealPeriod.DINNER, dinner);
        return result;
    }

    public Map<Integer, List<Student>> groupStudentsByGroupId(List<Student> students) {
        Map<Integer, List<Student>> grouped = new LinkedHashMap<>();

        for (Student student : students) {
            grouped.computeIfAbsent(student.getGroupId(), k -> new ArrayList<>()).add(student);
        }

        return grouped;
    }

    public List<Group> generateGroups() {
        List<Student> students = generateStudents();
        Map<Integer, List<Student>> grouped = groupStudentsByGroupId(students);

        List<Group> groups = new ArrayList<>();
        for (Map.Entry<Integer, List<Student>> entry : grouped.entrySet()) {
            List<Student> members = entry.getValue();
            long arrivalTime = members.get(0).getArrivalTime();
            groups.add(new Group(entry.getKey(), arrivalTime, members));
        }

        return groups;
    }

    public List<SimulationEvent> generateArrivalEvents(List<Student> students) {
        List<SimulationEvent> events = new ArrayList<>();

        for (Student student : students) {
            events.add(new SimulationEvent(
                    student.getArrivalTime(),
                    EventType.ARRIVAL,
                    student.getGroupId(),
                    student.getId(),
                    -1,
                    -1,
                    0,
                    student.getMealPeriod(),
                    student.getCrowdType(),
                    student.getArrivalSource()
            ));
        }

        Collections.sort(events);
        return events;
    }

    public List<SimulationEvent> generateArrivalEvents() {
        if (lastGenerationResult != null) {
            return new ArrayList<>(lastGenerationResult.getArrivalEvents());
        }
        return generateArrivalEvents(generateStudents());
    }

    private List<MealPeriod> resolvePeriods(SimulationMode mode, MealPeriod selectedPeriod) {
        List<MealPeriod> periods = new ArrayList<>();
        if (mode == SimulationMode.FULL_DAY) {
            periods.add(MealPeriod.BREAKFAST);
            periods.add(MealPeriod.LUNCH);
            periods.add(MealPeriod.DINNER);
        } else {
            periods.add(selectedPeriod);
        }
        return periods;
    }

    private PeriodGeneration generatePeriodStudents(MealPeriod period,
                                                    int population,
                                                    int simulationStartMinuteOfDay,
                                                    int[] counters) {
        int backgroundPopulation = (int) Math.round(population * CanteenConfig.BACKGROUND_FLOW_RATIO);
        backgroundPopulation = Math.min(Math.max(backgroundPopulation, 1), population);
        int peakPopulation = population - backgroundPopulation;

        List<ArrivalPeak> peaks = createPeaks(period, peakPopulation);
        List<ArrivalDistributionPoint> distributionPoints = createDistributionPoints(
                period,
                backgroundPopulation,
                peaks,
                simulationStartMinuteOfDay
        );

        List<ArrivalCandidate> candidates = new ArrayList<>();
        addBackgroundCandidates(candidates, period, backgroundPopulation, "background");
        addPeakCandidates(candidates, period, peakPopulation, peaks);

        candidates.sort(Comparator.comparingInt(candidate -> candidate.minuteOfDay));

        List<Student> students = new ArrayList<>();
        int index = 0;
        while (index < candidates.size()) {
            int remaining = candidates.size() - index;
            int groupSize = Math.min(determineGroupSize(), remaining);
            int groupId = counters[1]++;
            int groupMinute = candidates.get(index).minuteOfDay;
            CrowdType groupCrowdType = candidates.get(index).crowdType;
            String source = candidates.get(index).source;
            long groupArrivalTime = toSimulationMinute(groupMinute, simulationStartMinuteOfDay);

            for (int i = 0; i < groupSize; i++) {
                ArrivalCandidate candidate = candidates.get(index + i);

                students.add(new Student(
                        counters[0]++,
                        groupId,
                        groupArrivalTime,
                        generateDiningTime(),
                        generatePreferredWindow(),
                        generatePatience(groupCrowdType),
                        period,
                        groupCrowdType,
                        source
                ));
            }
            index += groupSize;
        }

        MealArrivalStats stats = new MealArrivalStats(
                period,
                population,
                backgroundPopulation,
                peakPopulation,
                peaks,
                distributionPoints
        );

        return new PeriodGeneration(students, stats);
    }

    private List<ArrivalPeak> createPeaks(MealPeriod period, int peakPopulation) {
        List<ArrivalPeak> peaks = new ArrayList<>();

        if (period == MealPeriod.BREAKFAST) {
            peaks.add(createPeak("early-breakfast", period, 7 * 60 + 5, 12.0, peakPopulation * 0.45, "early_class"));
            peaks.add(createPeak("late-breakfast", period, 8 * 60, 15.0, peakPopulation * 0.55, "late_breakfast"));
        } else if (period == MealPeriod.LUNCH) {
            double weightSum = sum(CanteenConfig.LUNCH_BATCH_WEIGHTS);
            for (int i = 0; i < CanteenConfig.LUNCH_BATCH_WEIGHTS.length; i++) {
                double share = CanteenConfig.LUNCH_BATCH_WEIGHTS[i] / weightSum;
                int mean = CanteenConfig.LUNCH_RELEASE_TIMES[i] + CanteenConfig.LUNCH_PEAK_DELAY_MEANS[i];
                peaks.add(createPeak("lunch-batch-" + (i + 1), period, mean, 9.0, peakPopulation * share, "lunch_batch_" + (i + 1)));
            }
        } else {
            int mainMean = CanteenConfig.DINNER_UNIFIED_RELEASE_TIME + CanteenConfig.DINNER_PEAK_DELAY_MEAN;
            peaks.add(createPeak("dinner-unified-release", period, mainMean, 14.0, peakPopulation * 0.75, "evening_class_release"));
            peaks.add(createPeak("dinner-late", period, 18 * 60 + 30, 18.0, peakPopulation * 0.25, "late_dinner"));
        }

        return peaks;
    }

    private ArrivalPeak createPeak(String name,
                                   MealPeriod period,
                                   int meanMinuteOfDay,
                                   double sigmaMinutes,
                                   double populationShare,
                                   String source) {
        double amplitude = populationShare / Math.max(1.0, sigmaMinutes * SQRT_TWO_PI);
        return new ArrivalPeak(name, period, meanMinuteOfDay, sigmaMinutes, amplitude, source);
    }

    private List<ArrivalDistributionPoint> createDistributionPoints(MealPeriod period,
                                                                    int backgroundPopulation,
                                                                    List<ArrivalPeak> peaks,
                                                                    int simulationStartMinuteOfDay) {
        List<ArrivalDistributionPoint> points = new ArrayList<>();
        double base = backgroundPopulation / (double) period.getDurationMinutes();

        for (int minute = period.getStartMinute(); minute <= period.getEndMinute(); minute++) {
            double peakIntensity = 0.0;
            for (ArrivalPeak peak : peaks) {
                peakIntensity += gaussianIntensity(minute, peak);
            }
            points.add(new ArrivalDistributionPoint(
                    period,
                    minute,
                    toSimulationMinute(minute, simulationStartMinuteOfDay),
                    base,
                    peakIntensity,
                    base + peakIntensity
            ));
        }

        return points;
    }

    private void addBackgroundCandidates(List<ArrivalCandidate> candidates,
                                         MealPeriod period,
                                         int count,
                                         String source) {
        for (int i = 0; i < count; i++) {
            int minute = period.getStartMinute() + random.nextInt(period.getDurationMinutes() + 1);
            candidates.add(new ArrivalCandidate(
                    clamp(minute, period.getStartMinute(), period.getEndMinute()),
                    determineCrowdType(),
                    source
            ));
        }
    }

    private void addPeakCandidates(List<ArrivalCandidate> candidates,
                                   MealPeriod period,
                                   int totalPeakPopulation,
                                   List<ArrivalPeak> peaks) {
        int created = 0;
        for (int i = 0; i < peaks.size(); i++) {
            ArrivalPeak peak = peaks.get(i);
            int count;
            if (i == peaks.size() - 1) {
                count = totalPeakPopulation - created;
            } else {
                double relative = peak.getAmplitudePerMinute() * peak.getSigmaMinutes() * SQRT_TWO_PI;
                count = (int) Math.round(relative);
                count = Math.min(count, totalPeakPopulation - created);
            }

            for (int j = 0; j < count; j++) {
                CrowdType crowdType = determineCrowdType();
                int delay = crowdDelayOffset(crowdType);
                int minute = (int) Math.round(peak.getMeanMinuteOfDay()
                        + random.nextGaussian() * peak.getSigmaMinutes()
                        + delay);
                candidates.add(new ArrivalCandidate(
                        clamp(minute, period.getStartMinute(), period.getEndMinute()),
                        crowdType,
                        peak.getSource()
                ));
            }
            created += count;
        }
    }

    private int toSimulationMinute(int minuteOfDay, int simulationStartMinuteOfDay) {
        return Math.max(0, minuteOfDay - simulationStartMinuteOfDay);
    }

    private double gaussianIntensity(int minuteOfDay, ArrivalPeak peak) {
        double distance = minuteOfDay - peak.getMeanMinuteOfDay();
        double sigma = peak.getSigmaMinutes();
        return peak.getAmplitudePerMinute() * Math.exp(-(distance * distance) / (2.0 * sigma * sigma));
    }

    private int determineGroupSize() {
        double r = random.nextDouble();

        if (r < CanteenConfig.PROB_SOLO) {
            return 1;
        }

        if (r < CanteenConfig.PROB_SOLO + CanteenConfig.PROB_DUO) {
            return 2;
        }

        return 4;
    }

    private int generateDiningTime() {
        double gaussian = random.nextGaussian();
        int result = (int) Math.round(
                CanteenConfig.DINING_TIME_MEAN + gaussian * CanteenConfig.DINING_TIME_STD
        );

        return Math.max(result, CanteenConfig.MIN_DINING_TIME);
    }

    private int generatePreferredWindow() {
        return random.nextInt(CanteenConfig.getWindowCount());
    }

    private int generatePatience(CrowdType crowdType) {
        int base = CanteenConfig.PATIENCE_MIN
                + random.nextInt(CanteenConfig.PATIENCE_MAX - CanteenConfig.PATIENCE_MIN + 1);

        if (crowdType == CrowdType.FAST) {
            return Math.max(CanteenConfig.PATIENCE_MIN, base - 2);
        }
        if (crowdType == CrowdType.SLOW) {
            return Math.min(CanteenConfig.PATIENCE_MAX + 3, base + 2);
        }
        return base;
    }

    private CrowdType determineCrowdType() {
        double r = random.nextDouble();
        if (r < 0.25) {
            return CrowdType.FAST;
        }
        if (r < 0.80) {
            return CrowdType.NORMAL;
        }
        return CrowdType.SLOW;
    }

    private int crowdDelayOffset(CrowdType crowdType) {
        if (crowdType == CrowdType.FAST) {
            return -Math.abs((int) Math.round(random.nextGaussian() * 2.0 + 3.0));
        }
        if (crowdType == CrowdType.SLOW) {
            return Math.abs((int) Math.round(random.nextGaussian() * 3.0 + 4.0));
        }
        return (int) Math.round(random.nextGaussian() * 2.0);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double sum(double[] values) {
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total;
    }

    private static class ArrivalCandidate {
        private final int minuteOfDay;
        private final CrowdType crowdType;
        private final String source;

        private ArrivalCandidate(int minuteOfDay, CrowdType crowdType, String source) {
            this.minuteOfDay = minuteOfDay;
            this.crowdType = crowdType;
            this.source = source;
        }
    }

    private static class PeriodGeneration {
        private final List<Student> students;
        private final MealArrivalStats stats;

        private PeriodGeneration(List<Student> students, MealArrivalStats stats) {
            this.students = students;
            this.stats = stats;
        }
    }
}
