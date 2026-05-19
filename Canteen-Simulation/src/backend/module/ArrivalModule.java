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
 * 2. Generate exactly the requested number of students for each selected meal period.
 * 3. Allocate arrivals on the configured time range by a truncated Gaussian distribution.
 *    The frontend openDuration is in minutes; generated arrivalTime is in seconds.
 * 4. Keep group sizes limited to 1, 2, 3 and 4.
 * 5. Provide visualization data for frontend charts.
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
     * Explicit entry for frontend/backend integration.
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
        List<ArrivalGenerationResult.PhaseBoundary> phaseBoundaries = new ArrayList<>();

        int[] counters = {1, 1};
        int simulationBaseSecond = 0;

        for (int i = 0; i < periods.size(); i++) {
            MealPeriod period = periods.get(i);
            int population = populations.get(period);
            int periodStartSecond = simulationBaseSecond;

            PeriodGeneration generation = generatePeriodStudents(
                    period,
                    population,
                    simulationBaseSecond,
                    counters
            );
            students.addAll(generation.students);
            mealStats.put(period, generation.stats);

            int periodEndSecond = simulationBaseSecond + getConfiguredDurationSeconds();
            phaseBoundaries.add(new ArrivalGenerationResult.PhaseBoundary(
                    mealPeriodChineseName(period),
                    buildPhaseLabel(period),
                    periodStartSecond,
                    mode == SimulationMode.FULL_DAY ? periodEndSecond : -1
            ));

            if (mode == SimulationMode.FULL_DAY) {
                simulationBaseSecond = periodEndSecond;

                if (i < periods.size() - 1 && CanteenConfig.MEAL_GAP_TICKS > 0) {
                    int gapStart = simulationBaseSecond;
                    int gapEnd = simulationBaseSecond + CanteenConfig.MEAL_GAP_TICKS;
                    phaseBoundaries.add(new ArrivalGenerationResult.PhaseBoundary(
                            "关闭中",
                            "食堂关闭中",
                            gapStart,
                            gapEnd
                    ));
                    simulationBaseSecond = gapEnd;
                }
            }
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
                mealStats,
                phaseBoundaries
        );

        if (queue != null) {
            queue.clear();
            queue.addAll(students);
        }

        return lastGenerationResult;
    }

    /**
     * The frontend input is now treated as the exact number of students in each
     * selected meal period. For a single-period simulation, entering breakfast
     * 1000 will generate exactly 1000 breakfast students. For full-day mode,
     * the same value is used for breakfast, lunch and dinner respectively.
     */
    public Map<MealPeriod, Integer> deriveMealPopulations(int totalPopulation) {
        Map<MealPeriod, Integer> result = new LinkedHashMap<>();
        result.put(MealPeriod.BREAKFAST, totalPopulation);
        result.put(MealPeriod.LUNCH, totalPopulation);
        result.put(MealPeriod.DINNER, totalPopulation);
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
                                                    int simulationBaseSecond,
                                                    int[] counters) {
        int configuredDurationSecond = getConfiguredDurationSeconds();
        double meanSecond = (configuredDurationSecond - 1) / 2.0;
        double sigmaSeconds = Math.max(1.0, configuredDurationSecond / 6.0);

        List<ArrivalPeak> peaks = createPeaks(period, population);
        List<ArrivalDistributionPoint> distributionPoints = createDistributionPoints(
                period,
                0,
                peaks,
                simulationBaseSecond / 60
        );

        List<Integer> groupSizes = generateGroupSizes(population);
        List<Student> students = new ArrayList<>();

        for (Integer groupSize : groupSizes) {
            int groupId = counters[1]++;
            CrowdType groupCrowdType = determineCrowdType();
            int groupSecond = sampleTruncatedGaussianSecond(0, configuredDurationSecond - 1, meanSecond, sigmaSeconds);
            long groupArrivalTime = simulationBaseSecond + groupSecond;

            for (int i = 0; i < groupSize; i++) {
                students.add(new Student(
                        counters[0]++,
                        groupId,
                        groupArrivalTime,
                        generateDiningTime(),
                        generatePreferredWindow(),
                        generatePatience(groupCrowdType),
                        period,
                        groupCrowdType,
                        "normal_distribution"
                ));
            }
        }

        students.sort(Comparator.comparingLong(Student::getArrivalTime)
                .thenComparingInt(Student::getGroupId)
                .thenComparingInt(Student::getId));

        MealArrivalStats stats = new MealArrivalStats(
                period,
                population,
                0,
                population,
                peaks,
                distributionPoints
        );

        return new PeriodGeneration(students, stats);
    }

    private List<ArrivalPeak> createPeaks(MealPeriod period, int peakPopulation) {
        int configuredStartMinute = period.getStartMinute();
        int configuredEndMinute = getConfiguredEndMinute(period);
        double meanMinute = (configuredStartMinute + configuredEndMinute) / 2.0;
        double sigmaMinutes = Math.max(1.0, getConfiguredDurationMinutes() / 6.0);

        List<ArrivalPeak> peaks = new ArrayList<>();
        peaks.add(createPeak(
                period.getCode() + "-normal-arrival",
                period,
                (int) Math.round(meanMinute),
                sigmaMinutes,
                peakPopulation,
                "normal_distribution"
        ));
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
                                                                    int simulationBaseMinute) {
        List<ArrivalDistributionPoint> points = new ArrayList<>();
        double base = 0.0;

        for (int minute = period.getStartMinute(); minute <= getConfiguredEndMinute(period); minute++) {
            double peakIntensity = 0.0;
            for (ArrivalPeak peak : peaks) {
                peakIntensity += gaussianIntensity(minute, peak);
            }
            points.add(new ArrivalDistributionPoint(
                    period,
                    minute,
                    toSimulationMinute(period, minute, simulationBaseMinute),
                    base,
                    peakIntensity,
                    base + peakIntensity
            ));
        }

        return points;
    }

    private void addBackgroundCandidates(List<ArrivalCandidate> candidates,
                                         MealPeriod period,
                                         int count) {
        int startMinute = period.getStartMinute();
        int endMinute = getConfiguredEndMinute(period);
        int duration = Math.max(1, getConfiguredDurationMinutes());

        for (int i = 0; i < count; i++) {
            double segmentStart = startMinute + (duration * i / (double) count);
            double segmentEnd = startMinute + (duration * (i + 1) / (double) count);
            int minute = (int) Math.floor(segmentStart + random.nextDouble() * Math.max(1.0, segmentEnd - segmentStart));
            candidates.add(new ArrivalCandidate(
                    clamp(minute, startMinute, endMinute),
                    determineCrowdType(),
                    "background"
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
                        clamp(minute, period.getStartMinute(), getConfiguredEndMinute(period)),
                        crowdType,
                        peak.getSource()
                ));
            }
            created += count;
        }
    }

    private List<Integer> generateGroupSizes(int population) {
        List<Integer> result = new ArrayList<>();
        int remaining = population;

        while (remaining > 0) {
            int groupSize = determineGroupSize(remaining);
            result.add(groupSize);
            remaining -= groupSize;
        }

        return result;
    }

    private int determineGroupSize(int remaining) {
        if (remaining <= 1) {
            return 1;
        }
        if (remaining == 2) {
            return chooseBetweenSoloAndDuo();
        }
        if (remaining == 3) {
            return chooseSizeForThree();
        }

        return determineGroupSize();
    }

    private int chooseBetweenSoloAndDuo() {
        double solo = Math.max(0.0, CanteenConfig.PROB_SOLO);
        double duo = Math.max(0.0, CanteenConfig.PROB_DUO);
        double sum = solo + duo;
        if (sum <= 0.0) {
            return 1;
        }
        return random.nextDouble() < solo / sum ? 1 : 2;
    }

    private int chooseSizeForThree() {
        double solo = Math.max(0.0, CanteenConfig.PROB_SOLO);
        double duo = Math.max(0.0, CanteenConfig.PROB_DUO);
        double trio = Math.max(0.0, CanteenConfig.PROB_TRIO);
        double sum = solo + duo + trio;
        if (sum <= 0.0) {
            return 1;
        }
        double r = random.nextDouble() * sum;
        if (r < solo) {
            return 1;
        }
        if (r < solo + duo) {
            return 2;
        }
        return 3;
    }

    private int sampleTruncatedGaussianMinute(int startMinute,
                                              int endMinute,
                                              double meanMinute,
                                              double sigmaMinutes) {
        for (int i = 0; i < 100; i++) {
            int minute = (int) Math.round(meanMinute + random.nextGaussian() * sigmaMinutes);
            if (minute >= startMinute && minute <= endMinute) {
                return minute;
            }
        }

        int fallback = (int) Math.round(meanMinute + random.nextGaussian() * sigmaMinutes);
        return clamp(fallback, startMinute, endMinute);
    }

    private int sampleTruncatedGaussianSecond(int startSecond,
                                              int endSecond,
                                              double meanSecond,
                                              double sigmaSeconds) {
        for (int i = 0; i < 100; i++) {
            int second = (int) Math.round(meanSecond + random.nextGaussian() * sigmaSeconds);
            if (second >= startSecond && second <= endSecond) {
                return second;
            }
        }

        int fallback = (int) Math.round(meanSecond + random.nextGaussian() * sigmaSeconds);
        return clamp(fallback, startSecond, endSecond);
    }

    private int getConfiguredDurationMinutes() {
        return Math.max(1, CanteenConfig.OPEN_DURATION);
    }

    private int getConfiguredDurationSeconds() {
        return getConfiguredDurationMinutes() * 60;
    }

    private int getConfiguredEndMinute(MealPeriod period) {
        return period.getStartMinute() + getConfiguredDurationMinutes() - 1;
    }

    private int toSimulationMinute(MealPeriod period, int minuteOfDay, int simulationBaseMinute) {
        return simulationBaseMinute + Math.max(0, minuteOfDay - period.getStartMinute());
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

        if (r < CanteenConfig.PROB_SOLO + CanteenConfig.PROB_DUO + CanteenConfig.PROB_TRIO) {
            return 3;
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
            return Math.max(CanteenConfig.PATIENCE_MIN, base - 2 * 60);
        }
        if (crowdType == CrowdType.SLOW) {
            return Math.min(CanteenConfig.PATIENCE_MAX + 3 * 60, base + 2 * 60);
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

    private static String mealPeriodChineseName(MealPeriod period) {
        if (period == null) {
            return "未知";
        }
        switch (period) {
            case BREAKFAST:
                return "早餐";
            case LUNCH:
                return "午餐";
            case DINNER:
                return "晚餐";
            default:
                return period.getDisplayName();
        }
    }

    private String buildPhaseLabel(MealPeriod period) {
        int startMinute = period.getStartMinute();
        int endMinute = getConfiguredEndMinute(period);
        return mealPeriodChineseName(period) + "时段 "
                + formatMinuteOfDay(startMinute) + "-"
                + formatMinuteOfDay(endMinute);
    }

    private static String formatMinuteOfDay(int minuteOfDay) {
        int normalized = Math.max(0, minuteOfDay);
        int hour = (normalized / 60) % 24;
        int minute = normalized % 60;
        return String.format("%02d:%02d", hour, minute);
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
