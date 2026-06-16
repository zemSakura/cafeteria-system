package backend.engine;

import backend.config.CanteenConfig;
import backend.dto.PressureLevel;
import backend.dto.RenderMode;
import backend.dto.SimulationSnapshot;
import backend.dto.TableStat;
import backend.dto.TableStatus;
import backend.dto.TrendPoint;
import backend.dto.WindowStat;
import backend.model.ArrivalGenerationResult;
import backend.model.StatisticsResult;
import backend.model.Student;
import backend.model.StudentStatus;
import backend.model.Table;
import backend.model.Window;
import backend.model.WindowState;
import backend.optimize.ReplayRecorder;
import backend.optimize.ReplaySnapshot;
import frontend.SimulationEventListener;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

/**
 * 文件说明：排队、入座与营业信息输出的核心实现文件。
 *
 * 作用定位：
 * 1. 这是后端正式运行的事件驱动仿真引擎，负责串联“学生到达 -> 选择窗口 -> 排队 -> 打饭 -> 等座 -> 入座 -> 就餐 -> 离开”的完整流程。
 * 2. 排队逻辑主要集中在 processArrivals、handleGroupArrival、estimateWindowWait、processServiceStarts、processServiceCompletions、updateQueueLength 等方法。
 * 3. 入座逻辑主要集中在 trySeatWaitingGroups、reserveSeatsForGroup、seatReadyMembersAtTable、findRandomFreeTableForGroup、findSharedTableForGroup、processDiningCompletions 等方法。
 * 4. 本文件保留队友新增的前端事件通知：学生到达、进入窗口队列、入座、离开、餐段切换都会实时通知 MainDashboard。
 * 5. 本文件同时负责输出营业信息文件：时间线 CSV、事件 CSV、摘要 TXT、HTML 报告。运行前会清理旧的 simulation-output 报告文件，避免无用文件越积越多。
 *
 * 核心规则：
 * - 后端仿真时间单位为秒。
 * - 学生按到达时间进入系统，同组学生统一判断是否放弃排队。
 * - 同组成员到达后，多数情况下同组选择同一窗口，少数情况下成员分散排队。
 * - 学生完成服务后进入等座区；同组中先打完饭的人可以先占座。
 * - 一旦找到桌子，后端会为整组锁定座位容量，防止后续同组成员没有座位。
 * - 前端只显示 Table 中真实已经入座的 seatGroupIds，锁定但未入座的位置不会提前标红。
 * - 同组成员全部实际入座并全部达到就餐结束时间后，整组一起离开并释放座位。
 */
public class SimulationEngine implements Runnable {

    private static final DateTimeFormatter REPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final double WALKING_SECONDS_PER_METER = 0.8;
    private static final double NON_PREFERRED_WINDOW_PENALTY_SECONDS = 8.0;
    private static final double GROUP_SHARED_WINDOW_PROBABILITY = 0.70;
    private static final SimulationEventListener NO_OP_LISTENER = new SimulationEventListener() {
        @Override
        public void onStudentArrived(int studentId, int groupId, long time) {
        }

        @Override
        public void onStudentQueuedAtWindow(int studentId, int groupId, int windowIndex, int queueLength, long time) {
        }

        @Override
        public void onWindowQueueUpdated(int windowIndex, int queueLength) {
        }

        @Override
        public void onTableOccupancyChanged(int tableIndex, int[] seatGroupIds) {
        }

        @Override
        public void onStudentSeatedAtTable(int studentId, int groupId, int tableIndex, long time) {
        }

        @Override
        public void onStudentLeft(int studentId, int groupId, int tableIndex, String reason, long time) {
        }

        @Override
        public void onPhaseChanged(String phaseName, String phaseLabel, long currentTime) {
        }

        @Override
        public void onSimulationFinished() {
        }
    };

    private final List<Student> allStudents;
    private final SimulationEventListener listener;

    private final List<WindowState> windowStates;
    private final List<Deque<Student>> windowQueues;
    private final List<Table> tables;
    private final Set<Student> diningStudents;
    private final PriorityQueue<DiningGroupCompletion> diningCompletionHeap;
    private final PriorityQueue<SeatWaitExpiration> seatWaitExpirationHeap;

    private final Student[] servingStudents;
    private final long[] serviceEndTimes;

    private final long timeScaleMillis;
    private final Random random;
    private final long hardStopTime;

    /** groupId -> 整组成员。 */
    private final Map<Integer, List<Student>> groupMembers;

    /** groupId -> 已完成打饭、正在等待入座的成员。 */
    private final Map<Integer, List<Student>> waitingSeatByGroup;

    /** 已整组放弃排队的 groupId。 */
    private final Set<Integer> abandonedGroups;

    /** groupId -> 已经为该组锁定的桌号。 */
    private final Map<Integer, Integer> groupAssignedTable;

    /** groupId -> 该组锁定的座位数，一般等于该组总人数。 */
    private final Map<Integer, Integer> groupReservedSeats;

    /** groupId -> 已经实际入座的成员。 */
    private final Map<Integer, List<Student>> seatedMembersByGroup;

    /** 新增等座成员后，需要尝试安排座位的小组。 */
    private final Set<Integer> seatGroupsToProcess;

    /**
     * tableId -> 当前桌子被占用/锁定的座位数。
     *
     * 该值用于后端容量判断，包含实际已入座座位和提前锁定座位。
     * 前端显示不使用该值，而是使用 Table.getSeatGroupIds()，因此锁定但未入座的位置不会标红。
     */
    private final Map<Integer, Integer> tableReservedOrOccupiedSeats;

    /** 已锁定或占用座位数 -> 对应桌号，用于快速查找可用桌位。 */
    private final List<RandomAccessIntSet> tableIdsByReservedSeatCount;

    private final List<ArrivalGenerationResult.PhaseBoundary> phaseBoundaries;
    private int currentPhaseIndex = -1;

    private volatile boolean running = true;

    private int nextArrivalIndex = 0;
    private long currentTime = 0;

    /** 对外保留一个“主报告路径”，现在指向 HTML 报告。 */
    private Path reportFilePath;
    private Path outputDir;
    private String reportBaseName;
    private Path timelineFilePath;
    private Path eventsFilePath;
    private Path summaryFilePath;
    private Path htmlReportFilePath;

    private PrintWriter timelineWriter;
    private PrintWriter eventWriter;
    private boolean timelineHeaderWritten = false;
    private boolean eventHeaderWritten = false;

    /** 用于最终生成摘要和 HTML 报告。 */
    private final List<Snapshot> snapshots = new ArrayList<>();

    private long[] cumulativeQueueLengthByWindow;
    private long[] busySampleCountByWindow;
    private long snapshotSampleCount = 0L;

    private int emptyTableSeatCount = 0;
    private int sharedTableSeatCount = 0;
    private int seatReservationCount = 0;
    private int groupLeaveCount = 0;
    private boolean retryAllWaitingSeatGroups = false;

    private final StatisticsResult statisticsResult = new StatisticsResult();

    /** Added for backend auto-optimization: tick-level samples for aggregate metrics. */
    private long queueLengthSum = 0L;
    private long queueSampleCount = 0L;
    private long occupiedSeatsSum = 0L;
    private long seatSampleCount = 0L;
    private long occupiedTableSum = 0L;
    private long tableSampleCount = 0L;
    private long busyWindowSum = 0L;
    private long windowSampleCount = 0L;
    private int maxTotalQueueLength = 0;
    private int maxWaitingSeatCount = 0;
    private long runStartWallMs = 0L;
    private long lastSnapshotBroadcastTime = -1L;
    private final List<TrendPoint> trendPoints = new ArrayList<>();
    private static final int MAX_TREND_POINTS = 240;

    /** Added for backend auto-optimization replay data. */
    private ReplayRecorder replayRecorder;

    public SimulationEngine(List<Student> students, SimulationEventListener listener) {
        this(students, listener, 50L, Collections.emptyList());
    }

    public SimulationEngine(List<Student> students,
                            SimulationEventListener listener,
                            long timeScaleMillis) {
        this(students, listener, timeScaleMillis, Collections.emptyList());
    }

    public SimulationEngine(List<Student> students,
                            SimulationEventListener listener,
                            long timeScaleMillis,
                            List<ArrivalGenerationResult.PhaseBoundary> phaseBoundaries) {
        this.listener = listener == null || !CanteenConfig.LISTENER_ENABLED ? NO_OP_LISTENER : listener;

        this.allStudents = new ArrayList<>();
        if (students != null) {
            this.allStudents.addAll(students);
        }

        this.allStudents.sort(
                Comparator.comparingLong(Student::getArrivalTime)
                        .thenComparingInt(Student::getGroupId)
                        .thenComparingInt(Student::getId)
        );

        // 队友前端传入的 timeScale 原本以“分钟 tick”为参照。
        // 现在后端 tick 是秒，因此压缩 sleep，避免 GUI 慢 60 倍。
        this.timeScaleMillis = Math.max(1L, Math.round(timeScaleMillis / 60.0));
        this.random = new Random(CanteenConfig.RANDOM_SEED);
        this.hardStopTime = calculateHardStopTime();

        this.windowStates = initWindowStates();
        this.windowQueues = new ArrayList<>();
        for (int i = 0; i < windowStates.size(); i++) {
            windowQueues.add(new ArrayDeque<>());
        }

        this.tables = initTables();
        this.diningStudents = new HashSet<>();
        this.diningCompletionHeap = new PriorityQueue<>(
                Comparator.comparingLong(completion -> completion.leaveTime));
        this.seatWaitExpirationHeap = new PriorityQueue<>(
                Comparator.comparingLong(expiration -> expiration.expirationTime));

        this.servingStudents = new Student[windowStates.size()];
        this.serviceEndTimes = new long[windowStates.size()];
        Arrays.fill(this.serviceEndTimes, -1L);

        this.groupMembers = new HashMap<>();
        this.waitingSeatByGroup = new HashMap<>();
        this.abandonedGroups = new HashSet<>();
        this.groupAssignedTable = new HashMap<>();
        this.groupReservedSeats = new HashMap<>();
        this.seatedMembersByGroup = new HashMap<>();
        this.seatGroupsToProcess = new LinkedHashSet<>();
        this.tableReservedOrOccupiedSeats = new HashMap<>();
        this.tableIdsByReservedSeatCount = initTableReservationBuckets();
        this.phaseBoundaries = new ArrayList<>(phaseBoundaries == null ? Collections.emptyList() : phaseBoundaries);

        this.cumulativeQueueLengthByWindow = new long[windowStates.size()];
        this.busySampleCountByWindow = new long[windowStates.size()];

        for (Student student : this.allStudents) {
            groupMembers.computeIfAbsent(student.getGroupId(), k -> new ArrayList<>()).add(student);
        }
    }

    public void requestStop() {
        running = false;
    }

    public Path getReportFilePath() {
        return reportFilePath;
    }

    public StatisticsResult getStatisticsResult() {
        return statisticsResult;
    }

    /** Added for backend auto-optimization: attach an optional recorder for best-plan replay. */
    public void setReplayRecorder(ReplayRecorder replayRecorder) {
        this.replayRecorder = replayRecorder;
    }

    @Override
    public void run() {
        try {
            runStartWallMs = System.currentTimeMillis();
            if (CanteenConfig.CSV_ENABLED) {
                openReportWriters();
            }
            checkAndBroadcastPhase();

            while (running) {
                processDiningCompletions();
                processServiceCompletions();
                processSeatWaitAbandonments();
                trySeatWaitingGroups();
                processArrivals();
                processServiceStarts();

                if (currentTime >= hardStopTime) {
                    closeUnfinishedAtHardStop();
                    sampleCurrentState();
                    break;
                }

                if (isFastForwardMode()) {
                    if (isSimulationFinished()) {
                        sampleCurrentState();
                        break;
                    }
                    long nextTime = findNextEventTimeAfterCurrent();
                    long duration = Math.max(1L, nextTime - currentTime);
                    sampleCurrentState(duration);
                    currentTime += duration;
                    checkAndBroadcastPhase();
                    continue;
                }

                sampleCurrentState();
                if (CanteenConfig.CSV_ENABLED) {
                    writeCurrentSnapshot();
                }
                recordReplaySnapshotIfNeeded();

                if (isSimulationFinished()) {
                    break;
                }

                if (!CanteenConfig.HEADLESS_MODE) {
                    Thread.sleep(timeScaleMillis);
                }
                currentTime++;
                checkAndBroadcastPhase();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            finalizeStatistics();
            broadcastSnapshot(true);
            closeReportWriters();
            if (CanteenConfig.CSV_ENABLED) {
                generateFinalReports();
            }
            running = false;
            listener.onSimulationFinished();
        }
    }

    private boolean isFastForwardMode() {
        return CanteenConfig.HEADLESS_MODE
                && !CanteenConfig.CSV_ENABLED
                && !CanteenConfig.REPLAY_RECORD_ENABLED;
    }

    private long findNextEventTimeAfterCurrent() {
        long nextTime = hardStopTime > currentTime ? hardStopTime : Long.MAX_VALUE;

        if (nextArrivalIndex < allStudents.size()) {
            long arrivalTime = allStudents.get(nextArrivalIndex).getArrivalTime();
            if (arrivalTime > currentTime) {
                nextTime = Math.min(nextTime, arrivalTime);
            }
        }

        for (long serviceEndTime : serviceEndTimes) {
            if (serviceEndTime > currentTime) {
                nextTime = Math.min(nextTime, serviceEndTime);
            }
        }

        while (!diningCompletionHeap.isEmpty()) {
            DiningGroupCompletion earliest = diningCompletionHeap.peek();
            if (!seatedMembersByGroup.containsKey(earliest.groupId)) {
                diningCompletionHeap.poll();
                continue;
            }
            if (earliest.leaveTime > currentTime) {
                nextTime = Math.min(nextTime, earliest.leaveTime);
            }
            break;
        }

        while (!seatWaitExpirationHeap.isEmpty()) {
            SeatWaitExpiration expiration = seatWaitExpirationHeap.peek();
            if (!waitingSeatByGroup.containsKey(expiration.groupId)) {
                seatWaitExpirationHeap.poll();
                continue;
            }
            if (expiration.expirationTime > currentTime) {
                nextTime = Math.min(nextTime, expiration.expirationTime);
            }
            break;
        }

        return nextTime == Long.MAX_VALUE ? currentTime + 1 : nextTime;
    }

    private long calculateHardStopTime() {
        long latestArrival = 0L;
        for (Student student : allStudents) {
            latestArrival = Math.max(latestArrival, student.getArrivalTime());
        }
        long diningAllowance = Math.max(
                60L * 60L,
                Math.round(CanteenConfig.DINING_TIME_MEAN + 4.0 * CanteenConfig.DINING_TIME_STD)
        );
        long waitingAllowance = Math.max(60L * 60L, CanteenConfig.PATIENCE_MAX * 2L);
        return latestArrival + diningAllowance + waitingAllowance;
    }

    private void closeUnfinishedAtHardStop() {
        String reason = "达到仿真结算边界";
        for (Student student : allStudents) {
            StudentStatus status = student.getStatus();
            if (status == StudentStatus.LEFT_NORMAL
                    || status == StudentStatus.BALKED
                    || status == StudentStatus.LEFT_NO_SEAT) {
                continue;
            }
            student.setStatus(status == StudentStatus.WAITING_SEAT || status == StudentStatus.DINING
                    ? StudentStatus.LEFT_NO_SEAT
                    : StudentStatus.BALKED);
            student.setLeaveReason(reason);
            student.setLeaveTime(currentTime);
            listener.onStudentLeft(student.getId(), student.getGroupId(), student.getTableId(), reason, currentTime);
        }

        for (Deque<Student> queue : windowQueues) {
            queue.clear();
        }
        for (int i = 0; i < servingStudents.length; i++) {
            servingStudents[i] = null;
            serviceEndTimes[i] = -1L;
            windowStates.get(i).setBusy(false);
            updateQueueLength(i);
        }
        for (Table table : tables) {
            Set<Integer> groups = new HashSet<>();
            for (int groupId : table.getSeatGroupIds()) {
                if (groupId >= 0) {
                    groups.add(groupId);
                }
            }
            for (Integer groupId : groups) {
                table.releaseSeats(groupId, currentTime);
            }
            listener.onTableOccupancyChanged(table.getId(), table.getSeatGroupIds());
        }
        waitingSeatByGroup.clear();
        diningStudents.clear();
        diningCompletionHeap.clear();
        seatWaitExpirationHeap.clear();
        tableReservedOrOccupiedSeats.clear();
        nextArrivalIndex = allStudents.size();
    }

    private void checkAndBroadcastPhase() {
        for (int i = currentPhaseIndex + 1; i < phaseBoundaries.size(); i++) {
            ArrivalGenerationResult.PhaseBoundary pb = phaseBoundaries.get(i);
            if (currentTime >= pb.startTick && (pb.endTick < 0 || currentTime < pb.endTick)) {
                currentPhaseIndex = i;
                listener.onPhaseChanged(pb.name, pb.label, currentTime);
                writeEvent(currentTime, "PHASE_CHANGE", -1, -1, -1, -1, -1, -1, -1,
                        "切换到餐段：" + pb.label);
                return;
            }
            if (pb.endTick >= 0 && currentTime >= pb.endTick) {
                continue;
            }
            break;
        }
    }

    /** 按组处理到达，多人组 70% 概率共同选择同一窗口。 */
    private void processArrivals() {
        while (nextArrivalIndex < allStudents.size()
                && allStudents.get(nextArrivalIndex).getArrivalTime() <= currentTime) {

            Student first = allStudents.get(nextArrivalIndex);
            int groupId = first.getGroupId();
            List<Student> members = groupMembers.get(groupId);

            if (members == null || members.isEmpty()) {
                nextArrivalIndex++;
                continue;
            }

            for (Student member : members) {
                if (member.getArrivalTime() <= currentTime
                        && member.getStatus() == StudentStatus.ARRIVING) {
                    listener.onStudentArrived(member.getId(), member.getGroupId(), currentTime);
                    writeEvent(currentTime, "ARRIVE", member.getId(), member.getGroupId(), -1, -1, -1, -1, -1,
                            "学生到达餐厅");
                }
            }

            handleGroupArrival(groupId);

            while (nextArrivalIndex < allStudents.size()
                    && allStudents.get(nextArrivalIndex).getGroupId() == groupId) {
                nextArrivalIndex++;
            }
        }
    }

    private void handleGroupArrival(int groupId) {
        if (abandonedGroups.contains(groupId)) {
            return;
        }

        List<Student> members = groupMembers.get(groupId);
        if (members == null || members.isEmpty()) {
            return;
        }

        Map<Student, Integer> selectedWindows = new HashMap<>();
        Map<Integer, Integer> addedCountByWindow = new HashMap<>();
        long maxEstimatedWait = 0L;
        Integer sharedWindowId = null;
        if (members.size() > 1 && random.nextDouble() < GROUP_SHARED_WINDOW_PROBABILITY) {
            sharedWindowId = chooseBestWindowForGroup(members);
        }

        for (Student student : members) {
            int windowId = sharedWindowId == null
                    ? chooseBestWindowForStudent(student, addedCountByWindow)
                    : sharedWindowId;
            selectedWindows.put(student, windowId);

            int alreadyAssignedToThisWindow = addedCountByWindow.getOrDefault(windowId, 0);
            long estimatedWait = estimateWindowWait(windowId)
                    + (long) alreadyAssignedToThisWindow * windowStates.get(windowId).getWindow().getAvgServeTime();
            maxEstimatedWait = Math.max(maxEstimatedWait, estimatedWait);

            addedCountByWindow.put(windowId, alreadyAssignedToThisWindow + 1);
        }

        double groupPatience = members.stream()
                .mapToInt(Student::getPatience)
                .average()
                .orElse(0);

        if (maxEstimatedWait > groupPatience) {
            abandonGroup(members, "预计等待超过耐心值");
            return;
        }

        Set<Integer> updatedWindows = new HashSet<>();
        for (Student student : members) {
            int windowId = selectedWindows.get(student);
            student.setFinalWindowId(windowId);
            student.setQueueEnterTime(currentTime);
            student.setStatus(StudentStatus.QUEUING);
            windowQueues.get(windowId).offerLast(student);
            updatedWindows.add(windowId);
            listener.onStudentQueuedAtWindow(student.getId(), student.getGroupId(), windowId,
                    windowQueues.get(windowId).size(), currentTime);
            writeEvent(currentTime, "QUEUE_ENTER", student.getId(), student.getGroupId(), windowId, -1, -1, -1, -1,
                    "学生进入窗口 " + (windowId + 1) + " 排队");
        }

        for (Integer windowId : updatedWindows) {
            updateQueueLength(windowId);
        }
    }

    private int chooseBestWindowForStudent(Student student, Map<Integer, Integer> addedCountByWindow) {
        int preferredWindow = Math.floorMod(student.getPreferredWindow(), windowQueues.size());
        int bestWindow = 0;
        double bestScore = Double.MAX_VALUE;

        for (int windowId = 0; windowId < windowQueues.size(); windowId++) {
            Window window = windowStates.get(windowId).getWindow();
            int addedCount = addedCountByWindow.getOrDefault(windowId, 0);
            double score = estimateWindowWait(windowId)
                    + (long) addedCount * window.getAvgServeTime()
                    + window.getDistanceFromDoor() * WALKING_SECONDS_PER_METER;
            if (windowId != preferredWindow) {
                score += NON_PREFERRED_WINDOW_PENALTY_SECONDS;
            }
            if (score < bestScore) {
                bestScore = score;
                bestWindow = windowId;
            }
        }
        return bestWindow;
    }

    private int chooseBestWindowForGroup(List<Student> members) {
        int bestWindow = 0;
        double bestScore = Double.MAX_VALUE;
        int groupSize = members == null ? 0 : members.size();
        for (int windowId = 0; windowId < windowQueues.size(); windowId++) {
            Window window = windowStates.get(windowId).getWindow();
            double preferencePenalty = 0.0;
            if (members != null) {
                for (Student student : members) {
                    int preferredWindow = Math.floorMod(student.getPreferredWindow(), windowQueues.size());
                    if (windowId != preferredWindow) {
                        preferencePenalty += NON_PREFERRED_WINDOW_PENALTY_SECONDS;
                    }
                }
            }
            double score = estimateWindowWait(windowId)
                    + (long) Math.max(0, groupSize - 1) * window.getAvgServeTime()
                    + window.getDistanceFromDoor() * WALKING_SECONDS_PER_METER
                    + preferencePenalty / Math.max(1, groupSize);
            if (score < bestScore) {
                bestScore = score;
                bestWindow = windowId;
            }
        }
        return bestWindow;
    }

    private long estimateWindowWait(int windowId) {
        WindowState state = windowStates.get(windowId);
        long wait = (long) windowQueues.get(windowId).size() * state.getWindow().getAvgServeTime();

        if (servingStudents[windowId] != null) {
            wait += Math.max(1L, serviceEndTimes[windowId] - currentTime);
        }

        return wait;
    }

    private void abandonGroup(List<Student> members, String reason) {
        int groupId = members.get(0).getGroupId();
        abandonedGroups.add(groupId);

        for (Student student : members) {
            student.setStatus(StudentStatus.BALKED);
            student.setLeaveReason(reason);
            student.setLeaveTime(currentTime);
            listener.onStudentLeft(student.getId(), student.getGroupId(), -1, reason, currentTime);
            writeEvent(currentTime, "BALK", student.getId(), student.getGroupId(), student.getFinalWindowId(), -1, -1, -1, -1,
                    "学生放弃排队：" + reason);
        }
    }

    private void processServiceStarts() {
        for (int i = 0; i < windowStates.size(); i++) {
            if (servingStudents[i] != null) {
                continue;
            }

            Deque<Student> queue = windowQueues.get(i);
            if (queue.isEmpty()) {
                continue;
            }

            Student student = queue.pollFirst();
            WindowState state = windowStates.get(i);

            state.setBusy(true);
            state.incrementServedCount();

            student.setQueueLeaveTime(currentTime);
            student.setServiceStartTime(currentTime);
            student.setStatus(StudentStatus.SERVING);

            servingStudents[i] = student;
            serviceEndTimes[i] = currentTime + sampleServiceDuration(state.getWindow());

            long queueWait = Math.max(0L, currentTime - student.getQueueEnterTime());
            writeEvent(currentTime, "SERVE_START", student.getId(), student.getGroupId(), i, -1, queueWait, -1, -1,
                    "学生开始在窗口 " + (i + 1) + " 打饭");

            updateQueueLength(i);
        }
    }

    /**
     * Service duration uses a bounded normal fluctuation around the configured
     * mean. The engine-level seeded Random keeps repeated runs reproducible.
     */
    private long sampleServiceDuration(Window window) {
        double mean = Math.max(1.0, window.getAvgServeTime());
        double standardDeviation = Math.max(1.0, mean * 0.15);
        double sampled = mean + random.nextGaussian() * standardDeviation;
        double lowerBound = mean * 0.70;
        double upperBound = mean * 1.30;
        return Math.max(1L, Math.round(Math.max(lowerBound, Math.min(upperBound, sampled))));
    }

    private void processServiceCompletions() {
        for (int i = 0; i < windowStates.size(); i++) {
            Student student = servingStudents[i];
            if (student == null) {
                continue;
            }

            if (serviceEndTimes[i] > currentTime) {
                continue;
            }

            WindowState state = windowStates.get(i);

            student.setServiceEndTime(currentTime);
            state.setBusy(false);
            state.addBusyTime(Math.max(1, currentTime - student.getServiceStartTime()));

            servingStudents[i] = null;
            serviceEndTimes[i] = -1L;

            student.setStatus(StudentStatus.WAITING_SEAT);
            waitingSeatByGroup
                    .computeIfAbsent(student.getGroupId(), k -> new ArrayList<>())
                    .add(student);
            seatGroupsToProcess.add(student.getGroupId());
            long queueWait = Math.max(0L, student.getServiceStartTime() - student.getQueueEnterTime());
            long remainingPatience = Math.max(1L, student.getPatience() - queueWait);
            seatWaitExpirationHeap.offer(new SeatWaitExpiration(
                    student.getGroupId(),
                    currentTime + remainingPatience
            ));

            long serviceDuration = Math.max(0L, currentTime - student.getServiceStartTime());
            writeEvent(currentTime, "SERVE_END", student.getId(), student.getGroupId(), i, -1,
                    Math.max(0L, student.getServiceStartTime() - student.getQueueEnterTime()), -1, serviceDuration,
                    "学生打饭结束，进入等待座位状态");
        }
    }

    /**
     * 尝试让已打完饭成员入座。
     * 同组第一批成员找到桌位后，后端立即为整组锁定容量；后续成员打完饭后直接坐到原桌。
     */
    private void trySeatWaitingGroups() {
        List<Integer> groupIds;
        if (retryAllWaitingSeatGroups) {
            groupIds = new ArrayList<>(waitingSeatByGroup.keySet());
            retryAllWaitingSeatGroups = false;
            seatGroupsToProcess.clear();
        } else {
            groupIds = new ArrayList<>(seatGroupsToProcess);
            seatGroupsToProcess.clear();
        }
        groupIds.sort(Comparator.comparingLong(this::oldestSeatWaitStart));

        for (int groupId : groupIds) {
            List<Student> readyMembers = waitingSeatByGroup.get(groupId);
            if (readyMembers == null || readyMembers.isEmpty()) {
                waitingSeatByGroup.remove(groupId);
                continue;
            }

            List<Student> allMembers = groupMembers.get(groupId);
            if (allMembers == null || allMembers.isEmpty()) {
                waitingSeatByGroup.remove(groupId);
                continue;
            }
            if (shouldAbandonWhileWaitingForSeat(allMembers)) {
                abandonWaitingSeatGroup(groupId, allMembers);
                continue;
            }

            int tableId = groupAssignedTable.getOrDefault(groupId, -1);
            if (tableId < 0) {
                int groupSize = allMembers.size();
                tableId = findRandomFreeTableForGroup(groupSize);
                boolean assignedToEmptyTable = tableId >= 0;
                if (tableId < 0) {
                    tableId = findSharedTableForGroup(groupSize);
                }
                if (tableId < 0) {
                    continue;
                }
                reserveSeatsForGroup(groupId, tableId, groupSize);
                if (assignedToEmptyTable) {
                    emptyTableSeatCount++;
                } else {
                    sharedTableSeatCount++;
                }
            }

            seatReadyMembersAtTable(groupId, tableId, readyMembers);
            waitingSeatByGroup.remove(groupId);
        }
    }

    private void processSeatWaitAbandonments() {
        while (!seatWaitExpirationHeap.isEmpty()
                && seatWaitExpirationHeap.peek().expirationTime <= currentTime) {
            SeatWaitExpiration expiration = seatWaitExpirationHeap.poll();
            List<Student> allMembers = groupMembers.get(expiration.groupId);
            if (!waitingSeatByGroup.containsKey(expiration.groupId)
                    || allMembers == null
                    || allMembers.isEmpty()) {
                continue;
            }
            if (shouldAbandonWhileWaitingForSeat(allMembers)) {
                abandonWaitingSeatGroup(expiration.groupId, allMembers);
            }
        }
    }

    private long oldestSeatWaitStart(int groupId) {
        List<Student> members = waitingSeatByGroup.get(groupId);
        long oldest = Long.MAX_VALUE;
        if (members != null) {
            for (Student member : members) {
                if (member.getServiceEndTime() >= 0) {
                    oldest = Math.min(oldest, member.getServiceEndTime());
                }
            }
        }
        return oldest;
    }

    private boolean shouldAbandonWhileWaitingForSeat(List<Student> allMembers) {
        for (Student member : allMembers) {
            if (member.getServiceEndTime() < 0) {
                return false;
            }
        }
        for (Student member : allMembers) {
            long queueWait = Math.max(0L, member.getServiceStartTime() - member.getQueueEnterTime());
            long seatWait = Math.max(0L, currentTime - member.getServiceEndTime());
            if (queueWait + seatWait >= member.getPatience()) {
                return true;
            }
        }
        return false;
    }

    private void abandonWaitingSeatGroup(int groupId, List<Student> allMembers) {
        String reason = "排队与等座总等待超过耐心值";
        abandonedGroups.add(groupId);
        waitingSeatByGroup.remove(groupId);
        for (Student student : allMembers) {
            student.setStatus(StudentStatus.LEFT_NO_SEAT);
            student.setLeaveReason(reason);
            student.setLeaveTime(currentTime);
            listener.onStudentLeft(student.getId(), student.getGroupId(), -1, reason, currentTime);
            writeEvent(currentTime, "LEFT_NO_SEAT", student.getId(), student.getGroupId(),
                    student.getFinalWindowId(), -1,
                    Math.max(0L, student.getServiceStartTime() - student.getQueueEnterTime()),
                    Math.max(0L, currentTime - student.getServiceEndTime()),
                    -1,
                    "学生因等座过久离开");
        }
    }

    private void reserveSeatsForGroup(int groupId, int tableId, int groupSize) {
        Table table = tables.get(tableId);
        int oldReservedOrOccupied = tableReservedOrOccupiedSeats.getOrDefault(tableId, 0);
        int newReservedOrOccupied = oldReservedOrOccupied + groupSize;

        if (newReservedOrOccupied > table.getCapacity()) {
            throw new IllegalStateException("table capacity exceeded while reserving seats");
        }

        moveTableReservationBucket(tableId, oldReservedOrOccupied, newReservedOrOccupied);
        groupAssignedTable.put(groupId, tableId);
        groupReservedSeats.put(groupId, groupSize);
        tableReservedOrOccupiedSeats.put(tableId, newReservedOrOccupied);
        seatReservationCount++;

        writeEvent(currentTime, "SEAT_RESERVE", -1, groupId, -1, tableId, -1, -1, -1,
                "为小组 " + groupId + " 锁定桌 " + (tableId + 1) + " 的 " + groupSize + " 个座位容量");
    }

    private void seatReadyMembersAtTable(int groupId, int tableId, List<Student> readyMembers) {
        List<Student> seatedMembers = seatedMembersByGroup.computeIfAbsent(groupId, k -> new ArrayList<>());
        List<Student> newlySeated = new ArrayList<>();

        for (Student student : readyMembers) {
            if (student.getStatus() == StudentStatus.DINING) {
                continue;
            }
            newlySeated.add(student);
        }

        if (newlySeated.isEmpty()) {
            return;
        }

        Table table = tables.get(tableId);
        int assigned = table.assignSeats(groupId, newlySeated.size(), currentTime);
        if (assigned != newlySeated.size()) {
            throw new IllegalStateException("actual table seats are insufficient for ready group members");
        }

        for (Student student : newlySeated) {
            student.setTableId(tableId);
            student.setSeatAssignedTime(currentTime);
            student.setDiningStartTime(currentTime);
            student.setDiningEndTime(currentTime + Math.max(1, student.getDiningTime()));
            student.setStatus(StudentStatus.DINING);

            seatedMembers.add(student);
            diningStudents.add(student);
            listener.onStudentSeatedAtTable(student.getId(), student.getGroupId(), tableId, currentTime);

            long seatWait = Math.max(0L, currentTime - student.getServiceEndTime());
            writeEvent(currentTime, "SEAT", student.getId(), student.getGroupId(), student.getFinalWindowId(), tableId,
                    Math.max(0L, student.getServiceStartTime() - student.getQueueEnterTime()), seatWait, student.getDiningTime(),
                    "学生入座桌 " + (tableId + 1));
        }

        List<Student> allMembers = groupMembers.get(groupId);
        if (allMembers != null && seatedMembers.size() == allMembers.size()) {
            long leaveTime = currentTime;
            for (Student student : seatedMembers) {
                leaveTime = Math.max(leaveTime, student.getDiningEndTime());
            }
            diningCompletionHeap.offer(new DiningGroupCompletion(groupId, leaveTime));
        }

        listener.onTableOccupancyChanged(tableId, table.getSeatGroupIds());
    }

    /** 优先找能容纳整组人数的空桌。 */
    private int findRandomFreeTableForGroup(int groupSize) {
        return pickRandomTableWithCapacity(tableIdsByReservedSeatCount.get(0), groupSize);
    }

    /** 无空桌时才允许拼桌，且不能占用其他组已经锁定的容量。 */
    private int findSharedTableForGroup(int groupSize) {
        for (int reservedSeatCount = tableIdsByReservedSeatCount.size() - 1;
             reservedSeatCount > 0;
             reservedSeatCount--) {
            int tableId = pickRandomTableWithCapacity(
                    tableIdsByReservedSeatCount.get(reservedSeatCount),
                    groupSize
            );
            if (tableId >= 0) {
                return tableId;
            }
        }
        return -1;
    }

    private int pickRandomTableWithCapacity(RandomAccessIntSet tableIds, int requiredSeats) {
        int selectedTableId = tableIds.getRandom(random);
        if (selectedTableId < 0) {
            return -1;
        }
        Table table = tables.get(selectedTableId);
        int reservedOrOccupied = tableReservedOrOccupiedSeats.getOrDefault(selectedTableId, 0);
        return table.getCapacity() - reservedOrOccupied >= requiredSeats ? selectedTableId : -1;
    }

    /** 同组必须全部实际入座，并且全部达到各自就餐结束时间后，整组一起离开。 */
    private void processDiningCompletions() {
        Set<Integer> changedTables = new LinkedHashSet<>();

        while (!diningCompletionHeap.isEmpty()) {
            DiningGroupCompletion completion = diningCompletionHeap.peek();
            if (completion.leaveTime > currentTime) {
                break;
            }
            diningCompletionHeap.poll();

            int groupId = completion.groupId;
            List<Student> seatedMembers = seatedMembersByGroup.remove(groupId);
            if (seatedMembers == null || seatedMembers.isEmpty()) {
                continue;
            }

            int tableId = groupAssignedTable.getOrDefault(groupId, -1);
            if (tableId < 0) {
                tableId = seatedMembers.get(0).getTableId();
            }

            for (Student student : seatedMembers) {
                student.setStatus(StudentStatus.LEFT_NORMAL);
                student.setLeaveReason("正常就餐结束");
                student.setLeaveTime(completion.leaveTime);
                diningStudents.remove(student);
                listener.onStudentLeft(student.getId(), student.getGroupId(), tableId, student.getLeaveReason(), completion.leaveTime);
                writeEvent(completion.leaveTime, "LEAVE", student.getId(), student.getGroupId(), student.getFinalWindowId(), tableId,
                        Math.max(0L, student.getServiceStartTime() - student.getQueueEnterTime()),
                        Math.max(0L, student.getSeatAssignedTime() - student.getServiceEndTime()),
                        Math.max(0L, student.getDiningEndTime() - student.getDiningStartTime()),
                        "学生随小组一起离开");
            }

            if (tableId >= 0) {
                Table table = tables.get(tableId);
                table.releaseSeats(groupId, currentTime);

                int reservedSeats = groupReservedSeats.getOrDefault(groupId, seatedMembers.size());
                int oldReservedOrOccupied = tableReservedOrOccupiedSeats.getOrDefault(tableId, 0);
                int remainingReservedOrOccupied = oldReservedOrOccupied - reservedSeats;
                moveTableReservationBucket(tableId, oldReservedOrOccupied, Math.max(0, remainingReservedOrOccupied));
                if (remainingReservedOrOccupied <= 0) {
                    tableReservedOrOccupiedSeats.remove(tableId);
                } else {
                    tableReservedOrOccupiedSeats.put(tableId, remainingReservedOrOccupied);
                }

                changedTables.add(tableId);
            }

            groupAssignedTable.remove(groupId);
            groupReservedSeats.remove(groupId);
            retryAllWaitingSeatGroups = true;
            groupLeaveCount++;
        }

        for (int tableId : changedTables) {
            listener.onTableOccupancyChanged(tableId, tables.get(tableId).getSeatGroupIds());
        }
    }

    private boolean isSimulationFinished() {
        if (nextArrivalIndex < allStudents.size()) {
            return false;
        }

        for (Deque<Student> queue : windowQueues) {
            if (!queue.isEmpty()) {
                return false;
            }
        }

        for (Student student : servingStudents) {
            if (student != null) {
                return false;
            }
        }

        if (!waitingSeatByGroup.isEmpty()) {
            return false;
        }

        return diningStudents.isEmpty();
    }

    private void updateQueueLength(int windowId) {
        int len = windowQueues.get(windowId).size();
        windowStates.get(windowId).setCurrentQueueLength(len);
        listener.onWindowQueueUpdated(windowId, len);
    }

    private List<WindowState> initWindowStates() {
        List<WindowState> result = new ArrayList<>();
        for (int i = 0; i < CanteenConfig.getWindowCount(); i++) {
            Window window = new Window(
                    i,
                    CanteenConfig.WINDOW_DISTANCES[i],
                    CanteenConfig.WINDOW_AVG_SERVE_TIME[i]
            );
            result.add(new WindowState(window));
        }
        return result;
    }

    private List<Table> initTables() {
        List<Table> result = new ArrayList<>();
        for (int i = 0; i < CanteenConfig.TOTAL_TABLES; i++) {
            result.add(new Table(i, 4));
        }
        return result;
    }

    private List<RandomAccessIntSet> initTableReservationBuckets() {
        int maxCapacity = 0;
        for (Table table : tables) {
            maxCapacity = Math.max(maxCapacity, table.getCapacity());
        }

        List<RandomAccessIntSet> buckets = new ArrayList<>(maxCapacity + 1);
        for (int i = 0; i <= maxCapacity; i++) {
            buckets.add(new RandomAccessIntSet());
        }
        for (Table table : tables) {
            buckets.get(0).add(table.getId());
        }
        return buckets;
    }

    private void moveTableReservationBucket(int tableId, int oldSeatCount, int newSeatCount) {
        tableIdsByReservedSeatCount.get(oldSeatCount).remove(tableId);
        tableIdsByReservedSeatCount.get(newSeatCount).add(tableId);
    }

    /** Added for backend auto-optimization: collect aggregate samples without requiring CSV output. */
    private void sampleCurrentState() {
        sampleCurrentState(1L);
    }

    private void sampleCurrentState(long durationSeconds) {
        long duration = Math.max(1L, durationSeconds);
        int totalQueueLength = getTotalQueueLength();
        queueLengthSum += totalQueueLength * duration;
        queueSampleCount += duration;
        maxTotalQueueLength = Math.max(maxTotalQueueLength, totalQueueLength);

        occupiedSeatsSum += getActualSeatedSeatCount() * duration;
        seatSampleCount += duration;

        occupiedTableSum += getActualSeatedTableCount() * duration;
        tableSampleCount += duration;

        busyWindowSum += getBusyWindowCount() * duration;
        windowSampleCount += duration;
        maxWaitingSeatCount = Math.max(maxWaitingSeatCount, getWaitingSeatCount());
        broadcastSnapshot(false);
    }

    private void recordReplaySnapshotIfNeeded() {
        if (CanteenConfig.REPLAY_RECORD_ENABLED && replayRecorder != null) {
            replayRecorder.tryRecord((int) currentTime, buildReplaySnapshot());
        }
    }

    private ReplaySnapshot buildReplaySnapshot() {
        ReplaySnapshot snapshot = new ReplaySnapshot();
        snapshot.timeSecond = (int) currentTime;
        snapshot.timeMinute = currentTime / 60.0;
        snapshot.totalQueueLength = getTotalQueueLength();
        snapshot.windowQueueLengths = getWindowQueueLengths();
        snapshot.busyWindowCount = getBusyWindowCount();
        snapshot.totalWindowCount = windowStates.size();
        snapshot.occupiedSeats = getActualSeatedSeatCount();
        snapshot.totalSeats = getTotalSeatCount();
        snapshot.emptySeats = Math.max(0, snapshot.totalSeats - snapshot.occupiedSeats);
        snapshot.diningStudents = diningStudents.size();
        snapshot.waitingSeatStudents = getWaitingSeatCount();
        snapshot.windowUtilization = currentWindowUtilization();
        snapshot.tableUtilization = currentTableUtilization();

        int arrived = 0, served = 0, finished = 0, abandoned = 0;
        for (Student student : allStudents) {
            if (student.getStatus() != StudentStatus.ARRIVING) arrived++;
            if (student.getServiceEndTime() >= 0) served++;
            if (student.getStatus() == StudentStatus.LEFT_NORMAL) finished++;
            if (student.getStatus() == StudentStatus.BALKED
                    || student.getStatus() == StudentStatus.LEFT_NO_SEAT) abandoned++;
        }
        snapshot.arrivedStudents = arrived;
        snapshot.servedStudents = served;
        snapshot.finishedStudents = finished;
        snapshot.abandonedStudents = abandoned;
        return snapshot;
    }

    private void broadcastSnapshot(boolean force) {
        if (!CanteenConfig.LISTENER_ENABLED) {
            return;
        }
        long interval = Math.max(1L, CanteenConfig.SNAPSHOT_INTERVAL);
        if (!force && lastSnapshotBroadcastTime >= 0 && currentTime - lastSnapshotBroadcastTime < interval) {
            return;
        }
        if (force && lastSnapshotBroadcastTime == currentTime) {
            return;
        }

        SimulationSnapshot snapshot = buildSimulationSnapshot();
        TrendPoint point = new TrendPoint();
        point.timeSecond = currentTime;
        point.queueingCount = snapshot.queueingCount;
        point.waitingSeatCount = snapshot.waitingSeatCount;
        point.windowUtilizationRate = snapshot.windowUtilizationRate;
        point.tableUtilizationRate = snapshot.tableUtilizationRate;
        point.completedCount = snapshot.completedCount;
        point.abandonedCount = snapshot.abandonedCount;
        trendPoints.add(point);
        if (trendPoints.size() > MAX_TREND_POINTS) {
            trendPoints.remove(0);
        }
        snapshot.trendPoints = copyTrendPoints();
        lastSnapshotBroadcastTime = currentTime;
        listener.onSnapshot(snapshot);
    }

    /**
     * Builds the single source of truth consumed by the Swing decision
     * dashboard. All KPI values are calculated from current engine state.
     */
    private SimulationSnapshot buildSimulationSnapshot() {
        SimulationSnapshot snapshot = new SimulationSnapshot();
        snapshot.currentTime = currentTime;
        snapshot.totalStudents = allStudents.size();
        snapshot.renderMode = chooseRenderMode(allStudents.size());

        List<Long> queueWaits = new ArrayList<>();
        List<Long> seatWaits = new ArrayList<>();
        List<Long> stayTimes = new ArrayList<>();
        long[] perWindowWaitSum = new long[windowStates.size()];
        int[] perWindowWaitCount = new int[windowStates.size()];

        for (Student student : allStudents) {
            StudentStatus status = student.getStatus();
            if (status != StudentStatus.ARRIVING) snapshot.arrivedCount++;
            if (status == StudentStatus.QUEUING) snapshot.queueingCount++;
            if (status == StudentStatus.SERVING) snapshot.servingCount++;
            if (status == StudentStatus.WAITING_SEAT) snapshot.waitingSeatCount++;
            if (status == StudentStatus.DINING) snapshot.diningCount++;
            if (status == StudentStatus.LEFT_NORMAL) snapshot.completedCount++;
            if (status == StudentStatus.BALKED || status == StudentStatus.LEFT_NO_SEAT) snapshot.abandonedCount++;

            if (student.getQueueEnterTime() >= 0) {
                long queueEnd = student.getServiceStartTime() >= 0
                        ? student.getServiceStartTime()
                        : currentTime;
                long queueWait = Math.max(0L, queueEnd - student.getQueueEnterTime());
                queueWaits.add(queueWait);
                int windowId = student.getFinalWindowId();
                if (windowId >= 0 && windowId < perWindowWaitSum.length) {
                    perWindowWaitSum[windowId] += queueWait;
                    perWindowWaitCount[windowId]++;
                }
            }

            if (student.getServiceEndTime() >= 0) {
                long seatEnd = student.getSeatAssignedTime() >= 0
                        ? student.getSeatAssignedTime()
                        : (student.getLeaveTime() >= 0 ? student.getLeaveTime() : currentTime);
                seatWaits.add(Math.max(0L, seatEnd - student.getServiceEndTime()));
            }

            if (status != StudentStatus.ARRIVING) {
                long stayEnd = student.getLeaveTime() >= 0 ? student.getLeaveTime() : currentTime;
                stayTimes.add(Math.max(0L, stayEnd - student.getArrivalTime()));
            }
        }

        snapshot.completionRate = ratio(snapshot.completedCount, snapshot.totalStudents);
        snapshot.abandonRate = ratio(snapshot.abandonedCount, snapshot.totalStudents);
        snapshot.avgQueueWaitSeconds = average(queueWaits);
        snapshot.p95QueueWaitSeconds = percentile(queueWaits, 0.95);
        snapshot.avgSeatWaitSeconds = average(seatWaits);
        snapshot.p95SeatWaitSeconds = percentile(seatWaits, 0.95);
        snapshot.avgTotalStaySeconds = average(stayTimes);
        snapshot.p95TotalStaySeconds = percentile(stayTimes, 0.95);
        snapshot.maxQueueLength = maxTotalQueueLength;
        snapshot.maxWaitingSeatCount = maxWaitingSeatCount;
        snapshot.windowUtilizationRate = currentWindowUtilization();
        snapshot.tableUtilizationRate = currentTableUtilization();
        snapshot.seatUtilizationRate = currentSeatUtilization();
        populateEconomics(snapshot, snapshot.completedCount, snapshot.abandonedCount);
        snapshot.windowStats = buildWindowStats(perWindowWaitSum, perWindowWaitCount);
        snapshot.tableStats = buildTableStats();
        snapshot.tableMatrix = buildTableMatrix(snapshot.tableStats);
        snapshot.trendPoints = copyTrendPoints();
        return snapshot;
    }

    private List<WindowStat> buildWindowStats(long[] waitSums, int[] waitCounts) {
        List<WindowStat> stats = new ArrayList<>();
        double elapsed = Math.max(1.0, currentTime);
        for (int i = 0; i < windowStates.size(); i++) {
            WindowState state = windowStates.get(i);
            WindowStat stat = new WindowStat();
            stat.windowId = i;
            stat.type = state.getWindow().getAvgServeTime() <= 45 ? "FAST" : "SLOW";
            stat.queueLength = windowQueues.get(i).size();
            stat.serving = servingStudents[i] != null;
            stat.avgWaitMinutes = waitCounts[i] == 0 ? 0.0 : waitSums[i] / (double) waitCounts[i] / 60.0;
            stat.servedCount = state.getServedCount();
            long busySeconds = state.getTotalBusyTime();
            if (servingStudents[i] != null) {
                busySeconds += Math.max(0L, currentTime - servingStudents[i].getServiceStartTime());
            }
            stat.utilizationRate = clamp01(busySeconds / elapsed);
            double estimatedWaitMinutes = (stat.queueLength + (stat.serving ? 1 : 0))
                    * state.getWindow().getAvgServeTime() / 60.0;
            stat.pressureLevel = pressureForWait(estimatedWaitMinutes);
            stats.add(stat);
        }
        return stats;
    }

    private List<TableStat> buildTableStats() {
        List<TableStat> stats = new ArrayList<>(tables.size());
        for (Table table : tables) {
            TableStat stat = new TableStat();
            stat.tableId = table.getId();
            stat.capacity = table.getCapacity();
            stat.occupiedSeats = table.getOccupiedSeatCount();
            stat.reservedOrOccupiedSeats = tableReservedOrOccupiedSeats.getOrDefault(table.getId(), stat.occupiedSeats);
            stat.seatGroupIds = table.getSeatGroupIds();
            stat.expectedReleaseTime = expectedReleaseTime(table.getId());
            if (stat.occupiedSeats == 0) {
                stat.status = TableStatus.EMPTY;
            } else if (stat.expectedReleaseTime >= currentTime
                    && stat.expectedReleaseTime - currentTime <= 5 * 60L) {
                stat.status = TableStatus.RELEASING_SOON;
            } else if (stat.occupiedSeats >= stat.capacity) {
                stat.status = TableStatus.FULL;
            } else if (stat.occupiedSeats >= Math.max(1, stat.capacity - 1)) {
                stat.status = TableStatus.NEAR_FULL;
            } else {
                stat.status = TableStatus.PARTIAL;
            }
            stats.add(stat);
        }
        return stats;
    }

    private long expectedReleaseTime(int tableId) {
        long releaseTime = -1L;
        for (Student student : diningStudents) {
            if (student.getTableId() == tableId) {
                releaseTime = Math.max(releaseTime, student.getDiningEndTime());
            }
        }
        return releaseTime;
    }

    private int[][] buildTableMatrix(List<TableStat> stats) {
        if (stats.isEmpty()) {
            return new int[0][0];
        }
        int columns = Math.min(10, Math.max(1, (int) Math.ceil(Math.sqrt(stats.size()))));
        int rows = (int) Math.ceil(stats.size() / (double) columns);
        int[][] matrix = new int[rows][columns];
        for (int i = 0; i < stats.size(); i++) {
            matrix[i / columns][i % columns] = tableStatusCode(stats.get(i).status);
        }
        return matrix;
    }

    private int tableStatusCode(TableStatus status) {
        switch (status) {
            case PARTIAL:
                return 1;
            case NEAR_FULL:
                return 2;
            case FULL:
                return 3;
            case RELEASING_SOON:
                return 4;
            default:
                return 0;
        }
    }

    private void populateEconomics(SimulationSnapshot snapshot, int completed, int abandoned) {
        double openHours = CanteenConfig.getEffectiveOpenHours();
        snapshot.grossRevenue = completed * CanteenConfig.AVG_MEAL_PRICE;
        snapshot.windowCost = windowStates.size() * CanteenConfig.WINDOW_COST_PER_HOUR * openHours;
        snapshot.tableCost = tables.size() * CanteenConfig.TABLE_COST;
        snapshot.lostOpportunityCost = abandoned * CanteenConfig.LOST_STUDENT_PENALTY;
        snapshot.netProfit = snapshot.grossRevenue
                - snapshot.windowCost
                - snapshot.tableCost
                - snapshot.lostOpportunityCost;
    }

    private List<TrendPoint> copyTrendPoints() {
        List<TrendPoint> copy = new ArrayList<>(trendPoints.size());
        for (TrendPoint point : trendPoints) {
            copy.add(point.copy());
        }
        return copy;
    }

    private RenderMode chooseRenderMode(int studentCount) {
        if (studentCount <= 300) {
            return RenderMode.INDIVIDUAL;
        }
        if (studentCount <= 1500) {
            return RenderMode.GROUPED;
        }
        return RenderMode.DENSITY;
    }

    private PressureLevel pressureForWait(double estimatedWaitMinutes) {
        if (estimatedWaitMinutes < 3.0) {
            return PressureLevel.LOW;
        }
        if (estimatedWaitMinutes < 8.0) {
            return PressureLevel.MEDIUM;
        }
        if (estimatedWaitMinutes < 15.0) {
            return PressureLevel.HIGH;
        }
        return PressureLevel.OVERLOAD;
    }

    private double currentWindowUtilization() {
        return windowSampleCount == 0 || windowStates.isEmpty()
                ? 0.0
                : clamp01(busyWindowSum / (double) (windowSampleCount * windowStates.size()));
    }

    private double currentTableUtilization() {
        return tableSampleCount == 0 || tables.isEmpty()
                ? 0.0
                : clamp01(occupiedTableSum / (double) (tableSampleCount * tables.size()));
    }

    private double currentSeatUtilization() {
        return seatSampleCount == 0 || getTotalSeatCount() == 0
                ? 0.0
                : clamp01(occupiedSeatsSum / (double) (seatSampleCount * getTotalSeatCount()));
    }

    private double average(List<Long> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        long total = 0L;
        for (Long value : values) {
            total += value;
        }
        return total / (double) values.size();
    }

    private double ratio(int numerator, int denominator) {
        return denominator <= 0 ? 0.0 : numerator / (double) denominator;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** Added for backend auto-optimization: produce final statistics for adapters and loss evaluation. */
    private void finalizeStatistics() {
        int totalStudents = allStudents.size();
        int arrivedStudents = 0;
        int servedStudents = 0;
        int finishedStudents = 0;
        int abandonedStudents = 0;
        double totalWait = 0.0;
        int waitCount = 0;
        List<Long> waitTimes = new ArrayList<>();
        List<Long> seatWaitTimes = new ArrayList<>();
        List<Long> totalStayTimes = new ArrayList<>();
        for (Student student : allStudents) {
            if (student.getStatus() != StudentStatus.ARRIVING) arrivedStudents++;
            if (student.getServiceEndTime() >= 0) servedStudents++;
            if (student.getStatus() == StudentStatus.LEFT_NORMAL) finishedStudents++;
            if (student.getStatus() == StudentStatus.BALKED
                    || student.getStatus() == StudentStatus.LEFT_NO_SEAT) abandonedStudents++;
            long waitTime = student.getWaitTime();
            if (waitTime >= 0) {
                totalWait += waitTime;
                waitCount++;
                waitTimes.add(waitTime);
            }
            if (student.getServiceEndTime() >= 0) {
                long seatWaitEnd = student.getSeatAssignedTime() >= 0
                        ? student.getSeatAssignedTime()
                        : (student.getLeaveTime() >= 0 ? student.getLeaveTime() : currentTime);
                seatWaitTimes.add(Math.max(0L, seatWaitEnd - student.getServiceEndTime()));
            }
            if (student.getStatus() != StudentStatus.ARRIVING) {
                long stayEnd = student.getLeaveTime() >= 0 ? student.getLeaveTime() : currentTime;
                totalStayTimes.add(Math.max(0L, stayEnd - student.getArrivalTime()));
            }
        }
        double avgWaitSeconds = waitCount == 0 ? 0.0 : totalWait / waitCount;
        double p95WaitSeconds = percentile(waitTimes, 0.95);
        double avgSeatWaitSeconds = average(seatWaitTimes);
        double p95SeatWaitSeconds = percentile(seatWaitTimes, 0.95);
        double avgTotalStaySeconds = average(totalStayTimes);
        double p95TotalStaySeconds = percentile(totalStayTimes, 0.95);
        double avgQueueLength = queueSampleCount == 0 ? 0.0 : queueLengthSum * 1.0 / queueSampleCount;
        double seatUtilization = seatSampleCount == 0 || getTotalSeatCount() == 0
                ? 0.0
                : occupiedSeatsSum * 1.0 / (seatSampleCount * getTotalSeatCount());
        double tableUtilization = tableSampleCount == 0 || tables.isEmpty()
                ? 0.0
                : occupiedTableSum * 1.0 / (tableSampleCount * tables.size());
        double windowUtilization = windowSampleCount == 0 || windowStates.isEmpty()
                ? 0.0
                : busyWindowSum * 1.0 / (windowSampleCount * windowStates.size());
        double openHours = CanteenConfig.getEffectiveOpenHours();
        double grossRevenue = finishedStudents * CanteenConfig.AVG_MEAL_PRICE;
        double windowCost = windowStates.size() * CanteenConfig.WINDOW_COST_PER_HOUR * openHours;
        double tableCost = tables.size() * CanteenConfig.TABLE_COST;
        double lostOpportunityCost = abandonedStudents * CanteenConfig.LOST_STUDENT_PENALTY;

        statisticsResult.setWindowCount(windowStates.size());
        statisticsResult.setTableCount(tables.size());
        statisticsResult.setTotalStudents(totalStudents);
        statisticsResult.setArrivedStudents(arrivedStudents);
        statisticsResult.setServedStudents(servedStudents);
        statisticsResult.setFinishedStudents(finishedStudents);
        statisticsResult.setAbandonedStudents(abandonedStudents);
        statisticsResult.setAvgWaitTimeSeconds(avgWaitSeconds);
        statisticsResult.setAvgWaitTimeMinutes(avgWaitSeconds / 60.0);
        statisticsResult.setP95WaitTimeSeconds(p95WaitSeconds);
        statisticsResult.setAvgSeatWaitTimeSeconds(avgSeatWaitSeconds);
        statisticsResult.setP95SeatWaitTimeSeconds(p95SeatWaitSeconds);
        statisticsResult.setAvgTotalStayTimeSeconds(avgTotalStaySeconds);
        statisticsResult.setP95TotalStayTimeSeconds(p95TotalStaySeconds);
        statisticsResult.setMaxQueueLength(maxTotalQueueLength);
        statisticsResult.setMaxWaitingSeatCount(maxWaitingSeatCount);
        statisticsResult.setAvgQueueLength(avgQueueLength);
        statisticsResult.setSeatUtilization(seatUtilization);
        statisticsResult.setTableUtilization(tableUtilization);
        statisticsResult.setWindowUtilization(windowUtilization);
        statisticsResult.setFinishRate(totalStudents == 0 ? 0.0 : finishedStudents * 1.0 / totalStudents);
        statisticsResult.setAbandonRate(totalStudents == 0 ? 0.0 : abandonedStudents * 1.0 / totalStudents);
        statisticsResult.setGrossRevenue(grossRevenue);
        statisticsResult.setWindowCost(windowCost);
        statisticsResult.setTableCost(tableCost);
        statisticsResult.setLostOpportunityCost(lostOpportunityCost);
        statisticsResult.setNetProfit(grossRevenue - windowCost - tableCost - lostOpportunityCost);
        statisticsResult.setRuntimeMs(runStartWallMs == 0L ? 0L : System.currentTimeMillis() - runStartWallMs);
    }

    private double percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private void openReportWriters() {
        if (!CanteenConfig.CSV_ENABLED) {
            return;
        }
        try {
            outputDir = Paths.get("simulation-output").toAbsolutePath();
            Files.createDirectories(outputDir);
            cleanOldGeneratedReports(outputDir);

            reportBaseName = LocalDateTime.now().format(REPORT_TIME_FORMATTER);
            timelineFilePath = outputDir.resolve("simulation_timeline_" + reportBaseName + ".csv");
            eventsFilePath = outputDir.resolve("simulation_events_" + reportBaseName + ".csv");
            summaryFilePath = outputDir.resolve("simulation_summary_" + reportBaseName + ".txt");
            htmlReportFilePath = outputDir.resolve("simulation_report_" + reportBaseName + ".html");
            reportFilePath = htmlReportFilePath;

            timelineWriter = new PrintWriter(Files.newBufferedWriter(timelineFilePath, StandardCharsets.UTF_8));
            eventWriter = new PrintWriter(Files.newBufferedWriter(eventsFilePath, StandardCharsets.UTF_8));

            writeTimelineHeader();
            writeEventHeader();

            System.out.println("Simulation timeline file: " + timelineFilePath);
            System.out.println("Simulation events file: " + eventsFilePath);
            System.out.println("Simulation summary file: " + summaryFilePath);
            System.out.println("Simulation HTML report: " + htmlReportFilePath);
        } catch (IOException e) {
            timelineWriter = null;
            eventWriter = null;
            reportFilePath = null;
            System.err.println("Failed to create simulation report files: " + e.getMessage());
        }
    }

    private void cleanOldGeneratedReports(Path dir) {
        String[] patterns = {
                "simulation_result_*.csv",
                "simulation_timeline_*.csv",
                "simulation_events_*.csv",
                "simulation_summary_*.txt",
                "simulation_report_*.html"
        };

        for (String pattern : patterns) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, pattern)) {
                for (Path file : stream) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ignored) {
                        // 删除失败不影响仿真运行；新报告仍会按时间戳生成。
                    }
                }
            } catch (IOException ignored) {
                // 目录不存在或遍历失败时直接跳过。
            }
        }
    }

    private void writeTimelineHeader() {
        if (timelineWriter == null || timelineHeaderWritten) {
            return;
        }

        timelineWriter.println(
                "timeSecond," +
                        "timeMinute," +
                        "phase," +
                        "totalStudents," +
                        "notArrivedStudents," +
                        "arrivedStudents," +
                        "windowQueues," +
                        "totalQueuing," +
                        "servingStudents," +
                        "waitingForSeatStudents," +
                        "diningStudents," +
                        "actualSeatedTables," +
                        "actualSeatedSeats," +
                        "reservedOrOccupiedTables," +
                        "reservedOrOccupiedSeats," +
                        "lockedOnlySeats," +
                        "leftNormally," +
                        "balkedStudents," +
                        "leftNoSeatStudents," +
                        "tableUtilization," +
                        "reservedCapacityUtilization," +
                        "actualTableSeatGroups," +
                        "tableReservationCounts"
        );
        timelineHeaderWritten = true;
    }

    private void writeEventHeader() {
        if (eventWriter == null || eventHeaderWritten) {
            return;
        }
        eventWriter.println(
                "timeSecond," +
                        "timeMinute," +
                        "eventType," +
                        "studentId," +
                        "groupId," +
                        "windowId," +
                        "tableId," +
                        "queueWaitSecond," +
                        "seatWaitSecond," +
                        "durationSecond," +
                        "description"
        );
        eventHeaderWritten = true;
    }

    private void writeCurrentSnapshot() {
        int interval = Math.max(1, CanteenConfig.SNAPSHOT_INTERVAL);
        boolean shouldWrite = currentTime == 0 || currentTime % interval == 0 || isSimulationFinished();
        if (!shouldWrite) {
            return;
        }

        Snapshot snapshot = buildCurrentSnapshot();
        snapshots.add(snapshot);
        updateSampleAccumulators(snapshot);

        if (timelineWriter == null) {
            return;
        }

        timelineWriter.println(
                snapshot.timeSecond + "," +
                        formatDouble(snapshot.timeSecond / 60.0) + "," +
                        escapeCsv(snapshot.phase) + "," +
                        snapshot.totalStudents + "," +
                        snapshot.notArrivedStudents + "," +
                        snapshot.arrivedStudents + "," +
                        escapeCsv(snapshot.windowQueues) + "," +
                        snapshot.totalQueuing + "," +
                        snapshot.servingStudents + "," +
                        snapshot.waitingForSeatStudents + "," +
                        snapshot.diningStudents + "," +
                        snapshot.actualSeatedTables + "," +
                        snapshot.actualSeatedSeats + "," +
                        snapshot.reservedOrOccupiedTables + "," +
                        snapshot.reservedOrOccupiedSeats + "," +
                        snapshot.lockedOnlySeats + "," +
                        snapshot.leftNormally + "," +
                        snapshot.balkedStudents + "," +
                        snapshot.leftNoSeatStudents + "," +
                        formatDouble(snapshot.tableUtilization) + "," +
                        formatDouble(snapshot.reservedCapacityUtilization) + "," +
                        escapeCsv(snapshot.actualTableSeatGroups) + "," +
                        escapeCsv(snapshot.tableReservationCounts)
        );
    }

    private Snapshot buildCurrentSnapshot() {
        int totalStudents = allStudents.size();
        int arrivedStudents = 0, leftNormally = 0, balkedStudents = 0, leftNoSeatStudents = 0;
        for (Student student : allStudents) {
            StudentStatus s = student.getStatus();
            if (s != StudentStatus.ARRIVING) arrivedStudents++;
            if (s == StudentStatus.LEFT_NORMAL) leftNormally++;
            else if (s == StudentStatus.BALKED) balkedStudents++;
            else if (s == StudentStatus.LEFT_NO_SEAT) leftNoSeatStudents++;
        }
        int notArrivedStudents = totalStudents - arrivedStudents;
        int totalQueuing = getTotalQueueLength();
        int servingCount = getServingCount();
        int waitingForSeatCount = getWaitingSeatCount();
        int diningCount = diningStudents.size();
        int actualSeatedTableCount = getActualSeatedTableCount();
        int actualSeatedSeatCount = diningStudents.size();
        int reservedOrOccupiedTableCount = getReservedOrOccupiedTableCount();
        int reservedOrOccupiedSeatCount = getReservedOrOccupiedSeatCount();
        int lockedOnlySeatCount = Math.max(0, reservedOrOccupiedSeatCount - actualSeatedSeatCount);
        int totalSeats = tables.size() * 4;
        double tableUtilization = totalSeats <= 0 ? 0.0 : actualSeatedSeatCount * 100.0 / totalSeats;
        double reservedCapacityUtilization = totalSeats <= 0 ? 0.0 : reservedOrOccupiedSeatCount * 100.0 / totalSeats;

        return new Snapshot(
                currentTime,
                getCurrentPhaseName(),
                totalStudents,
                notArrivedStudents,
                arrivedStudents,
                formatWindowQueues(),
                totalQueuing,
                servingCount,
                waitingForSeatCount,
                diningCount,
                actualSeatedTableCount,
                actualSeatedSeatCount,
                reservedOrOccupiedTableCount,
                reservedOrOccupiedSeatCount,
                lockedOnlySeatCount,
                leftNormally,
                balkedStudents,
                leftNoSeatStudents,
                tableUtilization,
                reservedCapacityUtilization,
                formatActualTableSeatGroups(),
                formatTableReservationCounts()
        );
    }

    private void updateSampleAccumulators(Snapshot snapshot) {
        snapshotSampleCount++;
        for (int i = 0; i < windowQueues.size(); i++) {
            cumulativeQueueLengthByWindow[i] += windowQueues.get(i).size();
            if (servingStudents[i] != null) {
                busySampleCountByWindow[i]++;
            }
        }
    }

    private void writeEvent(long timeSecond,
                            String eventType,
                            int studentId,
                            int groupId,
                            int windowId,
                            int tableId,
                            long queueWaitSecond,
                            long seatWaitSecond,
                            long durationSecond,
                            String description) {
        if (eventWriter == null) {
            return;
        }
        eventWriter.println(
                timeSecond + "," +
                        formatDouble(timeSecond / 60.0) + "," +
                        escapeCsv(eventType) + "," +
                        emptyIfNegative(studentId) + "," +
                        emptyIfNegative(groupId) + "," +
                        emptyIfNegative(windowId < 0 ? -1 : windowId + 1) + "," +
                        emptyIfNegative(tableId < 0 ? -1 : tableId + 1) + "," +
                        emptyIfNegative(queueWaitSecond) + "," +
                        emptyIfNegative(seatWaitSecond) + "," +
                        emptyIfNegative(durationSecond) + "," +
                        escapeCsv(description)
        );
    }

    private void closeReportWriters() {
        if (timelineWriter != null) {
            timelineWriter.flush();
            timelineWriter.close();
            timelineWriter = null;
        }
        if (eventWriter != null) {
            eventWriter.flush();
            eventWriter.close();
            eventWriter = null;
        }
    }

    private void generateFinalReports() {
        if (summaryFilePath == null || htmlReportFilePath == null) {
            return;
        }
        SummaryStats stats = calculateSummaryStats();
        writeSummaryReport(stats);
        writeHtmlReport(stats);
    }

    private SummaryStats calculateSummaryStats() {
        SummaryStats stats = new SummaryStats();
        stats.totalStudents = allStudents.size();

        int arrivedStudents = 0, leftNormally = 0, balked = 0, leftNoSeat = 0, stillArriving = 0;
        long queueWaitSum = 0L;
        int queueWaitCount = 0;
        long seatWaitSum = 0L;
        int seatWaitCount = 0;
        long stayTimeSum = 0L;
        int stayTimeCount = 0;

        for (Student student : allStudents) {
            StudentStatus s = student.getStatus();
            if (s != StudentStatus.ARRIVING) arrivedStudents++;
            else stillArriving++;
            if (s == StudentStatus.LEFT_NORMAL) leftNormally++;
            else if (s == StudentStatus.BALKED) balked++;
            else if (s == StudentStatus.LEFT_NO_SEAT) leftNoSeat++;

            if (student.getServiceStartTime() >= 0 && student.getQueueEnterTime() >= 0) {
                long queueWait = Math.max(0L, student.getServiceStartTime() - student.getQueueEnterTime());
                queueWaitSum += queueWait;
                queueWaitCount++;
                stats.maxQueueWait = Math.max(stats.maxQueueWait, queueWait);
            }
            if (student.getSeatAssignedTime() >= 0 && student.getServiceEndTime() >= 0) {
                long seatWait = Math.max(0L, student.getSeatAssignedTime() - student.getServiceEndTime());
                seatWaitSum += seatWait;
                seatWaitCount++;
                stats.maxSeatWait = Math.max(stats.maxSeatWait, seatWait);
            }
            if (student.getLeaveTime() >= 0) {
                long stayTime = Math.max(0L, student.getLeaveTime() - student.getArrivalTime());
                stayTimeSum += stayTime;
                stayTimeCount++;
            }
        }

        stats.arrivedStudents = arrivedStudents;
        stats.leftNormally = leftNormally;
        stats.balkedStudents = balked;
        stats.leftNoSeatStudents = leftNoSeat;
        stats.inSystemAtEnd = stats.totalStudents - leftNormally - balked - leftNoSeat - stillArriving;
        stats.completionRate = stats.totalStudents == 0 ? 0.0 : leftNormally * 100.0 / stats.totalStudents;
        stats.balkRate = stats.totalStudents == 0 ? 0.0 : balked * 100.0 / stats.totalStudents;

        stats.avgQueueWait = queueWaitCount == 0 ? 0.0 : queueWaitSum / (double) queueWaitCount;
        stats.avgSeatWait = seatWaitCount == 0 ? 0.0 : seatWaitSum / (double) seatWaitCount;
        stats.avgStayTime = stayTimeCount == 0 ? 0.0 : stayTimeSum / (double) stayTimeCount;

        for (Snapshot snapshot : snapshots) {
            stats.maxTotalQueuing = Math.max(stats.maxTotalQueuing, snapshot.totalQueuing);
            stats.maxServingStudents = Math.max(stats.maxServingStudents, snapshot.servingStudents);
            stats.maxWaitingSeatStudents = Math.max(stats.maxWaitingSeatStudents, snapshot.waitingForSeatStudents);
            stats.maxActualSeatedSeats = Math.max(stats.maxActualSeatedSeats, snapshot.actualSeatedSeats);
            stats.maxLockedOnlySeats = Math.max(stats.maxLockedOnlySeats, snapshot.lockedOnlySeats);
            stats.avgTableUtilization += snapshot.tableUtilization;
            stats.avgReservedCapacityUtilization += snapshot.reservedCapacityUtilization;
        }
        if (!snapshots.isEmpty()) {
            stats.avgTableUtilization /= snapshots.size();
            stats.avgReservedCapacityUtilization /= snapshots.size();
        }

        return stats;
    }

    private void writeSummaryReport(SummaryStats stats) {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(summaryFilePath, StandardCharsets.UTF_8))) {
            writer.println("食堂就餐仿真系统营业摘要");
            writer.println("========================");
            writer.println("报告时间戳: " + reportBaseName);
            writer.println("主报告文件: " + htmlReportFilePath);
            writer.println();
            writer.println("一、基础配置");
            writer.println("总学生数: " + stats.totalStudents);
            writer.println("桌子数量: " + tables.size());
            writer.println("总座位数: " + (tables.size() * 4));
            writer.println("窗口数量: " + windowStates.size());
            writer.println("窗口平均服务时间: " + Arrays.toString(CanteenConfig.WINDOW_AVG_SERVE_TIME) + " 秒");
            writer.println("学生耐心范围: " + formatSeconds(CanteenConfig.PATIENCE_MIN) + " - " + formatSeconds(CanteenConfig.PATIENCE_MAX));
            writer.println("快照输出间隔: " + CanteenConfig.SNAPSHOT_INTERVAL + " 秒");
            writer.println();
            writer.println("二、就餐结果");
            writer.println("已到达学生数: " + stats.arrivedStudents);
            writer.println("正常完成就餐人数: " + stats.leftNormally);
            writer.println("放弃排队人数: " + stats.balkedStudents);
            writer.println("因无座离开人数: " + stats.leftNoSeatStudents);
            writer.println("结束时仍在系统内人数: " + stats.inSystemAtEnd);
            writer.println("完成率: " + formatPercent(stats.completionRate));
            writer.println("放弃率: " + formatPercent(stats.balkRate));
            writer.println();
            writer.println("三、等待与拥堵");
            writer.println("平均排队等待: " + formatSeconds(stats.avgQueueWait));
            writer.println("最大排队等待: " + formatSeconds(stats.maxQueueWait));
            writer.println("平均等座等待: " + formatSeconds(stats.avgSeatWait));
            writer.println("最大等座等待: " + formatSeconds(stats.maxSeatWait));
            writer.println("平均系统停留时间: " + formatSeconds(stats.avgStayTime));
            writer.println("最大总排队人数: " + stats.maxTotalQueuing);
            writer.println("最大等座人数: " + stats.maxWaitingSeatStudents);
            writer.println("最大实际入座人数: " + stats.maxActualSeatedSeats);
            writer.println("最大锁定但未实际入座座位数: " + stats.maxLockedOnlySeats);
            writer.println();
            writer.println("四、座位与窗口");
            writer.println("平均桌位利用率: " + formatPercent(stats.avgTableUtilization));
            writer.println("平均预留容量利用率: " + formatPercent(stats.avgReservedCapacityUtilization));
            writer.println("空桌入座次数: " + emptyTableSeatCount);
            writer.println("拼桌入座次数: " + sharedTableSeatCount);
            writer.println("座位锁定次数: " + seatReservationCount);
            writer.println("整组离开次数: " + groupLeaveCount);
            writer.println();
            writer.println("五、输出文件");
            writer.println("时间线 CSV: " + timelineFilePath);
            writer.println("事件 CSV: " + eventsFilePath);
            writer.println("摘要 TXT: " + summaryFilePath);
            writer.println("HTML 报告: " + htmlReportFilePath);
        } catch (IOException e) {
            System.err.println("Failed to write summary report: " + e.getMessage());
        }
    }

    private void writeHtmlReport(SummaryStats stats) {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(htmlReportFilePath, StandardCharsets.UTF_8))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html lang=\"zh-CN\">");
            writer.println("<head>");
            writer.println("<meta charset=\"UTF-8\">");
            writer.println("<title>食堂就餐仿真营业报告</title>");
            writer.println("<style>");
            writer.println("body{font-family:Arial,'Microsoft YaHei',sans-serif;margin:24px;background:#f6f7fb;color:#111827;}");
            writer.println("h1,h2{margin:0 0 14px;} .section{background:white;border-radius:12px;padding:18px;margin:16px 0;box-shadow:0 1px 5px rgba(0,0,0,.08);}");
            writer.println(".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px;} .card{background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;padding:12px;}");
            writer.println(".value{font-size:24px;font-weight:700;margin-top:6px;} table{border-collapse:collapse;width:100%;} th,td{border:1px solid #e5e7eb;padding:8px;text-align:left;} th{background:#f3f4f6;}");
            writer.println(".bar-wrap{background:#e5e7eb;border-radius:999px;height:14px;overflow:hidden;} .bar{height:14px;background:#4f46e5;} .muted{color:#6b7280;font-size:13px;}");
            writer.println("</style>");
            writer.println("</head><body>");
            writer.println("<h1>食堂就餐仿真营业报告</h1>");
            writer.println("<p class=\"muted\">生成时间戳：" + escapeHtml(reportBaseName) + "</p>");

            writer.println("<div class=\"section\"><h2>结果概览</h2><div class=\"grid\">");
            writeMetricCard(writer, "总学生数", String.valueOf(stats.totalStudents));
            writeMetricCard(writer, "完成就餐人数", String.valueOf(stats.leftNormally));
            writeMetricCard(writer, "完成率", formatPercent(stats.completionRate));
            writeMetricCard(writer, "放弃排队人数", String.valueOf(stats.balkedStudents));
            writeMetricCard(writer, "放弃率", formatPercent(stats.balkRate));
            writeMetricCard(writer, "平均排队等待", formatSeconds(stats.avgQueueWait));
            writeMetricCard(writer, "平均等座等待", formatSeconds(stats.avgSeatWait));
            writeMetricCard(writer, "平均停留时间", formatSeconds(stats.avgStayTime));
            writer.println("</div></div>");

            writer.println("<div class=\"section\"><h2>关键拥堵指标</h2>");
            writer.println("<table><tr><th>指标</th><th>数值</th></tr>");
            writeTableRow(writer, "最大总排队人数", String.valueOf(stats.maxTotalQueuing));
            writeTableRow(writer, "最大窗口服务中人数", String.valueOf(stats.maxServingStudents));
            writeTableRow(writer, "最大等座人数", String.valueOf(stats.maxWaitingSeatStudents));
            writeTableRow(writer, "最大实际入座人数", String.valueOf(stats.maxActualSeatedSeats));
            writeTableRow(writer, "最大锁定但未入座座位数", String.valueOf(stats.maxLockedOnlySeats));
            writeTableRow(writer, "平均桌位利用率", formatPercent(stats.avgTableUtilization));
            writeTableRow(writer, "平均预留容量利用率", formatPercent(stats.avgReservedCapacityUtilization));
            writer.println("</table></div>");

            writer.println("<div class=\"section\"><h2>窗口统计</h2>");
            writer.println("<table><tr><th>窗口</th><th>累计服务人数</th><th>最大队列长度</th><th>平均队列长度</th><th>忙碌采样占比</th></tr>");
            for (int i = 0; i < windowStates.size(); i++) {
                WindowState state = windowStates.get(i);
                double avgQueue = snapshotSampleCount == 0 ? 0.0 : cumulativeQueueLengthByWindow[i] / (double) snapshotSampleCount;
                double busyRate = snapshotSampleCount == 0 ? 0.0 : busySampleCountByWindow[i] * 100.0 / snapshotSampleCount;
                writer.println("<tr><td>窗口 " + (i + 1) + "</td><td>" + state.getServedCount() + "</td><td>" + state.getMaxQueueLength() + "</td><td>" + formatDouble(avgQueue) + "</td><td>" + formatPercent(busyRate) + "</td></tr>");
            }
            writer.println("</table></div>");

            writer.println("<div class=\"section\"><h2>完成率条形图</h2>");
            writer.println("<p>完成率</p><div class=\"bar-wrap\"><div class=\"bar\" style=\"width:" + Math.min(100.0, stats.completionRate) + "%\"></div></div>");
            writer.println("<p>放弃率</p><div class=\"bar-wrap\"><div class=\"bar\" style=\"width:" + Math.min(100.0, stats.balkRate) + "%;background:#dc2626\"></div></div>");
            writer.println("</div>");

            writer.println("<div class=\"section\"><h2>输出文件</h2><table><tr><th>文件</th><th>路径</th></tr>");
            writeTableRow(writer, "时间线 CSV", String.valueOf(timelineFilePath));
            writeTableRow(writer, "事件 CSV", String.valueOf(eventsFilePath));
            writeTableRow(writer, "摘要 TXT", String.valueOf(summaryFilePath));
            writeTableRow(writer, "HTML 报告", String.valueOf(htmlReportFilePath));
            writer.println("</table></div>");

            writer.println("</body></html>");
        } catch (IOException e) {
            System.err.println("Failed to write HTML report: " + e.getMessage());
        }
    }

    private void writeMetricCard(PrintWriter writer, String label, String value) {
        writer.println("<div class=\"card\"><div>" + escapeHtml(label) + "</div><div class=\"value\">" + escapeHtml(value) + "</div></div>");
    }

    private void writeTableRow(PrintWriter writer, String key, String value) {
        writer.println("<tr><td>" + escapeHtml(key) + "</td><td>" + escapeHtml(value) + "</td></tr>");
    }

    private int countStudentsInStatus(StudentStatus status) {
        int count = 0;
        for (Student student : allStudents) {
            if (student.getStatus() == status) {
                count++;
            }
        }
        return count;
    }

    private int countStudentsNotInStatus(StudentStatus status) {
        int count = 0;
        for (Student student : allStudents) {
            if (student.getStatus() != status) {
                count++;
            }
        }
        return count;
    }

    private int countServedStudents() {
        int count = 0;
        for (Student student : allStudents) {
            if (student.getServiceEndTime() >= 0) {
                count++;
            }
        }
        return count;
    }

    private int countAbandonedStudents() {
        int count = 0;
        for (Student student : allStudents) {
            if (student.getStatus() == StudentStatus.BALKED || student.getStatus() == StudentStatus.LEFT_NO_SEAT) {
                count++;
            }
        }
        return count;
    }

    private int getTotalQueueLength() {
        int total = 0;
        for (Deque<Student> queue : windowQueues) {
            total += queue.size();
        }
        return total;
    }

    private int[] getWindowQueueLengths() {
        int[] lengths = new int[windowQueues.size()];
        for (int i = 0; i < windowQueues.size(); i++) {
            lengths[i] = windowQueues.get(i).size();
        }
        return lengths;
    }

    private int getServingCount() {
        int count = 0;
        for (Student student : servingStudents) {
            if (student != null) {
                count++;
            }
        }
        return count;
    }

    private int getBusyWindowCount() {
        int count = 0;
        for (WindowState state : windowStates) {
            if (state.isBusy()) {
                count++;
            }
        }
        return count;
    }

    private int getWaitingSeatCount() {
        int count = 0;
        for (List<Student> members : waitingSeatByGroup.values()) {
            count += members.size();
        }
        return count;
    }

    private int getActualSeatedTableCount() {
        int count = 0;
        for (Table table : tables) {
            if (table.getOccupiedSeatCount() > 0) {
                count++;
            }
        }
        return count;
    }

    private int getActualSeatedSeatCount() {
        int count = 0;
        for (Table table : tables) {
            count += table.getOccupiedSeatCount();
        }
        return count;
    }

    private int getTotalSeatCount() {
        int count = 0;
        for (Table table : tables) {
            count += table.getCapacity();
        }
        return count;
    }

    private int getReservedOrOccupiedTableCount() {
        int count = 0;
        for (Integer occupiedSeats : tableReservedOrOccupiedSeats.values()) {
            if (occupiedSeats != null && occupiedSeats > 0) {
                count++;
            }
        }
        return count;
    }

    private int getReservedOrOccupiedSeatCount() {
        int count = 0;
        for (Integer occupiedSeats : tableReservedOrOccupiedSeats.values()) {
            if (occupiedSeats != null) {
                count += occupiedSeats;
            }
        }
        return count;
    }

    private String getCurrentPhaseName() {
        if (currentPhaseIndex >= 0 && currentPhaseIndex < phaseBoundaries.size()) {
            return phaseBoundaries.get(currentPhaseIndex).name;
        }
        return "未分段";
    }

    private String formatWindowQueues() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < windowQueues.size(); i++) {
            if (i > 0) {
                builder.append('|');
            }
            builder.append('W').append(i + 1).append('=').append(windowQueues.get(i).size());
        }
        return builder.toString();
    }

    private String formatActualTableSeatGroups() {
        List<String> parts = new ArrayList<>();
        for (Table table : tables) {
            if (table.getOccupiedSeatCount() <= 0) {
                continue;
            }
            parts.add("T" + (table.getId() + 1) + "=" + Arrays.toString(table.getSeatGroupIds()));
        }
        if (parts.isEmpty()) {
            return "none";
        }
        return String.join("|", parts);
    }

    private String formatTableReservationCounts() {
        if (tableReservedOrOccupiedSeats.isEmpty()) {
            return "none";
        }

        List<Integer> tableIds = new ArrayList<>(tableReservedOrOccupiedSeats.keySet());
        tableIds.sort(Integer::compareTo);

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tableIds.size(); i++) {
            int tableId = tableIds.get(i);
            if (i > 0) {
                builder.append('|');
            }
            builder.append('T').append(tableId + 1).append('=').append(tableReservedOrOccupiedSeats.get(tableId));
        }
        return builder.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "\"\"";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String emptyIfNegative(long value) {
        return value < 0 ? "" : String.valueOf(value);
    }

    private String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String formatPercent(double value) {
        return formatDouble(value) + "%";
    }

    private String formatSeconds(double seconds) {
        return formatSeconds(Math.round(seconds));
    }

    private String formatSeconds(long seconds) {
        if (seconds < 0) {
            return "-";
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (minutes <= 0) {
            return remainingSeconds + " 秒";
        }
        return minutes + " 分 " + remainingSeconds + " 秒";
    }

    private static class DiningGroupCompletion {
        private final int groupId;
        private final long leaveTime;

        private DiningGroupCompletion(int groupId, long leaveTime) {
            this.groupId = groupId;
            this.leaveTime = leaveTime;
        }
    }

    private static class SeatWaitExpiration {
        private final int groupId;
        private final long expirationTime;

        private SeatWaitExpiration(int groupId, long expirationTime) {
            this.groupId = groupId;
            this.expirationTime = expirationTime;
        }
    }

    private static class RandomAccessIntSet {
        private final List<Integer> values = new ArrayList<>();
        private final Map<Integer, Integer> indexes = new HashMap<>();

        private void add(int value) {
            if (indexes.containsKey(value)) {
                return;
            }
            indexes.put(value, values.size());
            values.add(value);
        }

        private void remove(int value) {
            Integer index = indexes.remove(value);
            if (index == null) {
                return;
            }
            int lastIndex = values.size() - 1;
            int lastValue = values.remove(lastIndex);
            if (index < lastIndex) {
                values.set(index, lastValue);
                indexes.put(lastValue, index);
            }
        }

        private int getRandom(Random random) {
            return values.isEmpty() ? -1 : values.get(random.nextInt(values.size()));
        }
    }

    private static class Snapshot {
        final long timeSecond;
        final String phase;
        final int totalStudents;
        final int notArrivedStudents;
        final int arrivedStudents;
        final String windowQueues;
        final int totalQueuing;
        final int servingStudents;
        final int waitingForSeatStudents;
        final int diningStudents;
        final int actualSeatedTables;
        final int actualSeatedSeats;
        final int reservedOrOccupiedTables;
        final int reservedOrOccupiedSeats;
        final int lockedOnlySeats;
        final int leftNormally;
        final int balkedStudents;
        final int leftNoSeatStudents;
        final double tableUtilization;
        final double reservedCapacityUtilization;
        final String actualTableSeatGroups;
        final String tableReservationCounts;

        Snapshot(long timeSecond,
                 String phase,
                 int totalStudents,
                 int notArrivedStudents,
                 int arrivedStudents,
                 String windowQueues,
                 int totalQueuing,
                 int servingStudents,
                 int waitingForSeatStudents,
                 int diningStudents,
                 int actualSeatedTables,
                 int actualSeatedSeats,
                 int reservedOrOccupiedTables,
                 int reservedOrOccupiedSeats,
                 int lockedOnlySeats,
                 int leftNormally,
                 int balkedStudents,
                 int leftNoSeatStudents,
                 double tableUtilization,
                 double reservedCapacityUtilization,
                 String actualTableSeatGroups,
                 String tableReservationCounts) {
            this.timeSecond = timeSecond;
            this.phase = phase;
            this.totalStudents = totalStudents;
            this.notArrivedStudents = notArrivedStudents;
            this.arrivedStudents = arrivedStudents;
            this.windowQueues = windowQueues;
            this.totalQueuing = totalQueuing;
            this.servingStudents = servingStudents;
            this.waitingForSeatStudents = waitingForSeatStudents;
            this.diningStudents = diningStudents;
            this.actualSeatedTables = actualSeatedTables;
            this.actualSeatedSeats = actualSeatedSeats;
            this.reservedOrOccupiedTables = reservedOrOccupiedTables;
            this.reservedOrOccupiedSeats = reservedOrOccupiedSeats;
            this.lockedOnlySeats = lockedOnlySeats;
            this.leftNormally = leftNormally;
            this.balkedStudents = balkedStudents;
            this.leftNoSeatStudents = leftNoSeatStudents;
            this.tableUtilization = tableUtilization;
            this.reservedCapacityUtilization = reservedCapacityUtilization;
            this.actualTableSeatGroups = actualTableSeatGroups;
            this.tableReservationCounts = tableReservationCounts;
        }
    }

    private static class SummaryStats {
        int totalStudents;
        int arrivedStudents;
        int leftNormally;
        int balkedStudents;
        int leftNoSeatStudents;
        int inSystemAtEnd;
        double completionRate;
        double balkRate;
        double avgQueueWait;
        long maxQueueWait;
        double avgSeatWait;
        long maxSeatWait;
        double avgStayTime;
        int maxTotalQueuing;
        int maxServingStudents;
        int maxWaitingSeatStudents;
        int maxActualSeatedSeats;
        int maxLockedOnlySeats;
        double avgTableUtilization;
        double avgReservedCapacityUtilization;
    }
}
