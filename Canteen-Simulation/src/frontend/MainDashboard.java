package frontend;

import backend.config.CanteenConfig;
import backend.engine.SimulationEngine;
import backend.model.Student;
import backend.module.ArrivalModule;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class MainDashboard extends JFrame implements SimulationEventListener {

    private static DiningAreaPanel myDiningPanel;
    private static QueueAreaPanel myQueuePanel;

    private static JButton startButton;
    private static JButton stopButton;

    private static MainDashboard frame;

    private static Thread simulationThread = null;
    private static SimulationEngine simulationEngine = null;

    private static JTextArea logTextArea;
    private static javax.swing.Timer delayTimer;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MainDashboard::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        frame = new MainDashboard();
        frame.setTitle("北京交通大学就餐仿真系统 - 总控台大屏");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800);
        frame.setLayout(new BorderLayout(10, 10));

        frame.add(createConfigPanel(), BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        myDiningPanel = new DiningAreaPanel(20);
        centerPanel.add(myDiningPanel);
        frame.add(centerPanel, BorderLayout.CENTER);

        myQueuePanel = new QueueAreaPanel(5);
        myQueuePanel.setPreferredSize(new Dimension(250, 0));
        frame.add(myQueuePanel, BorderLayout.EAST);

        frame.add(createLogAreaPanel(), BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JPanel createConfigPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Initialization Config"));

        startButton = new JButton("▶ 开始仿真");
        stopButton = new JButton("■ 停止仿真");
        stopButton.setEnabled(false);

        panel.add(startButton);
        panel.add(stopButton);

        startButton.addActionListener(e -> {
            SimulationConfigDialog configDialog = new SimulationConfigDialog(null);
            configDialog.setVisible(true);

            if (!configDialog.isConfirmed()) {
                return;
            }

            SimulationConfigDTO dto = configDialog.getConfigData();

            try {
                applyConfig(dto);

                myDiningPanel.updateTableCount(dto.totalTables);
                myQueuePanel.updateWindowCount(dto.windowCount);

                logTextArea.setText("");
                appendLog(">>> 初始化配置注入成功！桌数: " + dto.totalTables + " | 窗口: " + dto.windowCount);
                appendLog(">>> 正在点火，启动正式仿真引擎...");

                startButton.setEnabled(false);
                stopButton.setEnabled(false);

                delayTimer = new javax.swing.Timer(800, event -> {
                    try {
                        appendLog(">>> 仿真引擎启动成功！正在生成学生剧本...");

                        ArrivalModule arrivalModule = new ArrivalModule(CanteenConfig.RANDOM_SEED);
                        List<Student> students = arrivalModule.generateStudents();

                        appendLog(">>> 学生数据生成完成，共 " + students.size() + " 名学生。");

                        simulationEngine = new SimulationEngine(students, frame, 50L);
                        simulationThread = new Thread(simulationEngine, "SimulationEngine");
                        simulationThread.start();

                        stopButton.setEnabled(true);
                    } catch (Exception ex) {
                        appendLog(">>> [错误] 启动失败：" + ex.getMessage());
                        startButton.setEnabled(true);
                        stopButton.setEnabled(false);
                        ex.printStackTrace();
                    }
                });

                delayTimer.setRepeats(false);
                delayTimer.start();

            } catch (Exception ex) {
                appendLog(">>> [错误] 配置非法：" + ex.getMessage());
                JOptionPane.showMessageDialog(frame, ex.getMessage(), "配置错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        stopButton.addActionListener(e -> {
            if (delayTimer != null && delayTimer.isRunning()) {
                delayTimer.stop();
                appendLog(">>> [系统] 已取消点火，准备程序已拦截。");
            }

            if (simulationEngine != null) {
                simulationEngine.requestStop();
            }

            if (simulationThread != null && simulationThread.isAlive()) {
                simulationThread.interrupt();
                appendLog(">>> [系统] 收到停止指令，正在关闭引擎...");
            }

            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        });

        return panel;
    }

    private static void applyConfig(SimulationConfigDTO dto) {
        CanteenConfig.TOTAL_TABLES = dto.totalTables;
        CanteenConfig.OPEN_DURATION = dto.openDuration;
        CanteenConfig.RANDOM_SEED = dto.randomSeed;

        CanteenConfig.initWindowsConfig(dto.windowCount);

        CanteenConfig.PROB_SOLO = dto.probSolo;
        double remain = 1.0 - dto.probSolo;
        CanteenConfig.PROB_DUO = remain * 2.0 / 3.0;
        CanteenConfig.PROB_TEAM = remain / 3.0;

        CanteenConfig.validate();
    }

    private static JPanel createLogAreaPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Real-time Logs (实时日志)"));

        logTextArea = new JTextArea(8, 20);
        logTextArea.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(logTextArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private static void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            logTextArea.append(msg + "\n");
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }

    @Override
    public void onStudentArrived(int studentId, long time) {
        SwingUtilities.invokeLater(() -> {
            logTextArea.append(String.format(">>> [%03d] 学生 %03d 抵达食堂%n", time, studentId));
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }

    @Override
    public void onWindowQueueUpdated(int windowIndex, int queueLength) {
        SwingUtilities.invokeLater(() -> myQueuePanel.updateQueueLength(windowIndex, queueLength));
    }

    @Override
    public void onTableStatusChanged(int tableIndex, boolean isOccupied) {
        SwingUtilities.invokeLater(() -> {
            myDiningPanel.updateTableStatus(tableIndex, isOccupied);
            logTextArea.append(String.format(
                    ">>> [桌位] 桌 %02d %s%n",
                    tableIndex + 1,
                    isOccupied ? "已占用" : "已释放"
            ));
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }

    @Override
    public void onSimulationFinished() {
        SwingUtilities.invokeLater(() -> {
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            appendLog(">>> [系统] 本次仿真已结束。");
            JOptionPane.showMessageDialog(this, "本次仿真已圆满结束！");
        });
    }
}