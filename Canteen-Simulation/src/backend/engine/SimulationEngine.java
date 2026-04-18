package backend.engine;

import backend.config.CanteenConfig;
import frontend.SimulationEventListener;
import backend.model.Student;
import backend.model.StudentStatus;
import backend.model.Table;
import backend.model.Window;
import backend.model.WindowState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * 正式事件驱动仿真引擎
 *
 * 说明：
 * 1. 后端不直接操作任何 Swing 组件
 * 2. 所有界面变化都通过 SimulationEventListener 回调给前端
 * 3. 当前实现的是“最小可运行正式版”：
 *    到达 -> 入队 -> 服务 -> 入座 -> 就餐 -> 离开 -> 结束
 */
public class SimulationEngine implements Runnable {

    private final List<Student> allStudents;
    private final SimulationEventListener listener;

    private final List<WindowState> windowStates;
    private final List<Deque<Student>> windowQueues;
    private final List<Table> tables;
    private final List<Student> diningStudents;

    private final Student[] servingStudents;
    private final long[] serviceEndTimes;

    private final long timeScaleMillis;

    private volatile boolean running = true;

    private int nextArrivalIndex = 0;
    private long currentTime = 0;

    public SimulationEngine(List<Student> students, SimulationEventListener listener) {
        this(students, listener, 50L);
    }

    public SimulationEngine(List<Student> students,
                            SimulationEventListener listener,
                            long timeScaleMillis) {
        this.listener = Objects.requireNonNull(listener, "listener 不能为空");
        this.allStudents = new ArrayList<>(students == null ? List.of() : students);
        this.allStudents.sort(
                Comparator.comparingLong(Student::getArrivalTime)
                        .thenComparingInt(Student::getId)
        );

        this.timeScaleMillis = Math.max(1L, timeScaleMillis);

        this.windowStates = initWindowStates();
        this.windowQueues = new ArrayList<>();
        for (int i = 0; i < windowStates.size(); i++) {
            windowQueues.add(new ArrayDeque<>());
        }

        this.tables = initTables();
        this.diningStudents = new ArrayList<>();

        this.servingStudents = new Student[windowStates.size()];
        this.serviceEndTimes = new long[windowStates.size()];
        Arrays.fill(this.serviceEndTimes, -1L);
    }

    public void requestStop() {
        running = false;
    }

    @Override
    public void run() {
        try {
            while (running) {
                processDiningCompletions();
                processServiceCompletions();
                processArrivals();
                processServiceStarts();

                if (isSimulationFinished()) {
                    break;
                }

                Thread.sleep(timeScaleMillis);
                currentTime++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            running = false;
            listener.onSimulationFinished();
        }
    }

    private void processArrivals() {
        while (nextArrivalIndex < allStudents.size()
                && allStudents.get(nextArrivalIndex).getArrivalTime() <= currentTime) {
            Student student = allStudents.get(nextArrivalIndex++);
            listener.onStudentArrived(student.getId(), currentTime);
            enqueueStudent(student);
        }
    }

    private void enqueueStudent(Student student) {
        int windowId = chooseWindow(student);
        student.setFinalWindowId(windowId);
        student.setQueueEnterTime(currentTime);
        student.setStatus(StudentStatus.QUEUING);

        windowQueues.get(windowId).offerLast(student);
        updateQueueLength(windowId);
    }

    /**
     * 第一版先采用“偏好窗口优先”。
     * 如果偏好窗口非法，再退化到最短队列窗口。
     */
    private int chooseWindow(Student student) {
        int preferred = student.getPreferredWindow();
        if (preferred >= 0 && preferred < windowQueues.size()) {
            return preferred;
        }

        int bestWindow = 0;
        int bestLen = Integer.MAX_VALUE;

        for (int i = 0; i < windowQueues.size(); i++) {
            int len = windowQueues.get(i).size();
            if (len < bestLen) {
                bestLen = len;
                bestWindow = i;
            }
        }

        return bestWindow;
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
            serviceEndTimes[i] = currentTime + Math.max(1, state.getWindow().getAvgServeTime());

            updateQueueLength(i);
        }
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

            int freeTableId = findFreeTableId();
            if (freeTableId >= 0) {
                Table table = tables.get(freeTableId);
                table.occupy(student.getGroupId(), currentTime);

                student.setTableId(freeTableId);
                student.setSeatAssignedTime(currentTime);
                student.setDiningStartTime(currentTime);
                student.setDiningEndTime(currentTime + Math.max(1, student.getDiningTime()));
                student.setStatus(StudentStatus.DINING);

                diningStudents.add(student);
                listener.onTableStatusChanged(freeTableId, true);
            } else {
                student.setStatus(StudentStatus.LEFT_NO_SEAT);
                student.setLeaveReason("无空桌离开");
                student.setLeaveTime(currentTime);
            }
        }
    }

    private void processDiningCompletions() {
        Iterator<Student> iterator = diningStudents.iterator();
        while (iterator.hasNext()) {
            Student student = iterator.next();
            if (student.getDiningEndTime() > currentTime) {
                continue;
            }

            int tableId = student.getTableId();
            if (tableId >= 0 && tableId < tables.size()) {
                tables.get(tableId).release(currentTime);
                listener.onTableStatusChanged(tableId, false);
            }

            student.setStatus(StudentStatus.LEFT_NORMAL);
            student.setLeaveReason("正常就餐结束");
            student.setLeaveTime(currentTime);

            iterator.remove();
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

        return diningStudents.isEmpty();
    }

    private void updateQueueLength(int windowId) {
        int len = windowQueues.get(windowId).size();
        windowStates.get(windowId).setCurrentQueueLength(len);
        listener.onWindowQueueUpdated(windowId, len);
    }

    private int findFreeTableId() {
        for (Table table : tables) {
            if (!table.isOccupied()) {
                return table.getId();
            }
        }
        return -1;
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
}