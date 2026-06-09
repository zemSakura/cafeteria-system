package frontend;

import frontend.SimulationConfigDTO;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimulationConfigDialog extends JDialog {
    private JComboBox<String> modeComboBox;
    private JComboBox<String> mealComboBox;
    private final SimulationConfigDTO presetDto;
    private final boolean lockedFromOptimization;

    // UI 组件：输入框
    private JTextField tablesField;
    private JTextField durationField;
    private JTextField windowCountField;
    private JTextField probSoloField;
    private JTextField studentsField;
    private JTextField avgMealPriceField;
    private JTextField windowCostField;
    private JTextField tableCostField;
    private JTextField lostPenaltyField;

    // 状态与数据
    private boolean isConfirmed = false;
    private SimulationConfigDTO finalConfig = null;

    public SimulationConfigDialog(Frame parent) {
        this(parent, null);
    }

    public SimulationConfigDialog(Frame parent, SimulationConfigDTO presetDto) {
        // 设置为模态弹窗（不关掉它，就不能点后面的主界面）
        super(parent, "仿真参数初始化配置", true);
        setSize(520, 620);
        setLocationRelativeTo(parent); // 居中显示
        setLayout(new BorderLayout(10, 10));

        // 1. 初始化输入框并填入默认值
        SimulationConfigDTO defaultDto = presetDto == null ? new SimulationConfigDTO() : presetDto;
        this.presetDto = defaultDto;
        this.lockedFromOptimization = defaultDto.lockedFromOptimization;
        tablesField = new JTextField(String.valueOf(defaultDto.totalTables));
        durationField = new JTextField(String.valueOf(defaultDto.openDuration));
        windowCountField = new JTextField(String.valueOf(defaultDto.windowCount));
        probSoloField = new JTextField(String.valueOf(defaultDto.probSolo));
        avgMealPriceField = new JTextField(String.valueOf(defaultDto.avgMealPrice));
        windowCostField = new JTextField(String.valueOf(defaultDto.windowCostPerHour));
        tableCostField = new JTextField(String.valueOf(defaultDto.tableCost));
        lostPenaltyField = new JTextField(String.valueOf(defaultDto.lostStudentPenalty));

        // 2. 组装表单面板 (使用 GridLayout 两列排布)
        JPanel formPanel = new JPanel(new GridLayout(11, 2, 10, 12));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        formPanel.add(new JLabel(buildTableLabel()));
        formPanel.add(tablesField);

        formPanel.add(new JLabel("营业总时长 (分钟):"));
        formPanel.add(durationField);

        formPanel.add(new javax.swing.JLabel("食堂就餐总人数:"));
        studentsField = new javax.swing.JTextField(String.valueOf(defaultDto.totalStudents)); // 默认给个1000人
        formPanel.add(studentsField);

        formPanel.add(new JLabel(buildWindowLabel()));
        formPanel.add(windowCountField);

        formPanel.add(new JLabel("单人就餐概率 (0.0-1.0):"));
        formPanel.add(probSoloField);

        formPanel.add(new JLabel("平均客单价 (元):"));
        formPanel.add(avgMealPriceField);

        formPanel.add(new JLabel("窗口运营成本 (元/小时):"));
        formPanel.add(windowCostField);

        formPanel.add(new JLabel("餐桌成本 (元/张):"));
        formPanel.add(tableCostField);

        formPanel.add(new JLabel("放弃学生损失 (元/人):"));
        formPanel.add(lostPenaltyField);

        // 【新增】：模拟模式下拉框
        formPanel.add(new JLabel("模拟模式:"));
        String[] modes = {"单时段", "全天仿真"};
        modeComboBox = new JComboBox<>(modes);
        modeComboBox.setSelectedIndex("fullDay".equals(defaultDto.simulationMode) ? 1 : 0);
        formPanel.add(modeComboBox);

        // 【新增】：餐段选择下拉框
        formPanel.add(new JLabel("目标餐段:"));
        String[] meals = {"早餐", "午餐", "晚餐"};
        mealComboBox = new JComboBox<>(meals);
        // 默认选午餐，因为午餐逻辑最复杂
        if ("breakfast".equals(defaultDto.mealPeriod)) {
            mealComboBox.setSelectedIndex(0);
        } else if ("dinner".equals(defaultDto.mealPeriod)) {
            mealComboBox.setSelectedIndex(2);
        } else {
            mealComboBox.setSelectedIndex(1);
        }
        mealComboBox.setEnabled(modeComboBox.getSelectedIndex() == 0);
        formPanel.add(mealComboBox);

        // 【高阶联动体验】：如果选了全天，就把餐段下拉框置灰（因为全天模式不需要选单餐）
        modeComboBox.addActionListener(e -> {
            boolean isSingle = modeComboBox.getSelectedIndex() == 0;
            mealComboBox.setEnabled(isSingle && !lockedFromOptimization);
        });

        if (lockedFromOptimization) {
            JLabel lockNotice = new JLabel("复盘参数已锁定：请确认后运行");
            lockNotice.setBorder(BorderFactory.createEmptyBorder(10, 30, 0, 30));
            lockNotice.setForeground(frontend.ColorTheme.ACCENT_YELLOW);
            this.add(lockNotice, BorderLayout.NORTH);
        }
        this.add(formPanel, BorderLayout.CENTER);

        // 3. 组装底部按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton importBtn = new JButton("导入配置文件");
        JButton cancelBtn = new JButton("取消");
        JButton confirmBtn = new JButton("确认并启动");
        applyReplayLock(importBtn);

        buttonPanel.add(importBtn);
        buttonPanel.add(cancelBtn);
        buttonPanel.add(confirmBtn);
        this.add(buttonPanel, BorderLayout.SOUTH);

        // 4. 绑定事件：取消按钮
        cancelBtn.addActionListener(e -> {
            isConfirmed = false;
            dispose(); // 销毁弹窗
        });

        // 5. 绑定事件：确认按钮（核心校验逻辑）
        confirmBtn.addActionListener(e -> handleConfirm());

        // 6. 绑定事件：导入按钮（预留接口，下一步实现）
        importBtn.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            // 只允许选择 .json 文件
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("配置文件", "json"));

            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                try {
                    // 读取文件内容
                    String content = new String(Files.readAllBytes(selectedFile.toPath()));

                    // 手动解析（如果没有引入库，可以用正则提取数字）
                    tablesField.setText(extractJsonValue(content, "totalTables"));
                    durationField.setText(extractJsonValue(content, "openDuration"));
                    studentsField.setText(extractJsonValue(content, "totalStudents"));
                    windowCountField.setText(extractJsonValue(content, "windowCount"));
                    probSoloField.setText(extractJsonValue(content, "probSolo"));
                    avgMealPriceField.setText(extractJsonValue(content, "avgMealPrice"));
                    windowCostField.setText(extractJsonValue(content, "windowCostPerHour"));
                    tableCostField.setText(extractJsonValue(content, "tableCost"));
                    lostPenaltyField.setText(extractJsonValue(content, "lostStudentPenalty"));

                    JOptionPane.showMessageDialog(this, "配置已从文件自动填充！");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "文件解析失败，请检查格式！", "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private void applyReplayLock(JButton importBtn) {
        if (!lockedFromOptimization) {
            return;
        }
        tablesField.setEnabled(false);
        windowCountField.setEnabled(false);
        durationField.setEnabled(false);
        studentsField.setEnabled(false);
        probSoloField.setEnabled(false);
        avgMealPriceField.setEnabled(false);
        windowCostField.setEnabled(false);
        tableCostField.setEnabled(false);
        lostPenaltyField.setEnabled(false);
        modeComboBox.setEnabled(false);
        mealComboBox.setEnabled(false);
        importBtn.setEnabled(false);
    }

    // 辅助方法：从 JSON 中提取数字
    private String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\":\\s*([0-9.]+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String buildTableLabel() {
        return lockedFromOptimization ? "就餐区桌子总数:" : "就餐区桌子总数 (1-200):";
    }

    private String buildWindowLabel() {
        return "开放窗口数量:";
    }

    // --- 核心校验逻辑 ---
    private void handleConfirm() {
        try {
            SimulationConfigDTO dto = new SimulationConfigDTO();
            if (lockedFromOptimization) {
                copyLockedOptimizationFields(dto);
            } else {
                dto.totalTables = Integer.parseInt(tablesField.getText().trim());
                dto.windowCount = Integer.parseInt(windowCountField.getText().trim());
                dto.openDuration = Integer.parseInt(durationField.getText().trim());
                dto.probSolo = Double.parseDouble(probSoloField.getText().trim());
                dto.randomSeed = AdvancedOptimizationSettings.nextAutomaticRandomSeed();
                dto.totalStudents = Integer.parseInt(studentsField.getText().trim());
                dto.avgMealPrice = Double.parseDouble(avgMealPriceField.getText().trim());
                dto.windowCostPerHour = Double.parseDouble(windowCostField.getText().trim());
                dto.tableCost = Double.parseDouble(tableCostField.getText().trim());
                dto.lostStudentPenalty = Double.parseDouble(lostPenaltyField.getText().trim());
                dto.simulationMode = modeComboBox.getSelectedIndex() == 0 ? "singlePeriod" : "fullDay";

                int mealIdx = mealComboBox.getSelectedIndex();
                if (mealIdx == 0) dto.mealPeriod = "breakfast";
                else if (mealIdx == 1) dto.mealPeriod = "lunch";
                else dto.mealPeriod = "dinner";
            }

            validateReplayConfig(dto);

            // 业务规则校验
            if (dto.probSolo < 0.0 || dto.probSolo > 1.0) {
                throw new IllegalArgumentException("概率必须在 0 到 1 之间！");
            }
            if (dto.totalStudents <= 0 || dto.totalStudents > 10000) {
                throw new IllegalArgumentException("总人数应在 1 到 10000 之间！");
            }
            if (dto.avgMealPrice < 0.0 || dto.windowCostPerHour < 0.0
                    || dto.tableCost < 0.0 || dto.lostStudentPenalty < 0.0) {
                throw new IllegalArgumentException("经营参数不能为负数！");
            }

            // 校验通过，保存数据并关闭弹窗
            this.finalConfig = dto;
            this.isConfirmed = true;
            this.dispose();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "请输入有效的纯数字格式！", "格式错误", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "参数越界", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void validateReplayConfig(SimulationConfigDTO dto) {
        if (dto.lockedFromOptimization) {
            if (dto.windowCount < dto.minWindowCount || dto.windowCount > dto.maxWindowCount) {
                throw new IllegalArgumentException("窗口数量必须在寻优范围 "
                        + dto.minWindowCount + " 到 " + dto.maxWindowCount + " 之间！");
            }
            if (dto.totalTables < dto.minTableCount || dto.totalTables > dto.maxTableCount) {
                throw new IllegalArgumentException("桌子数量必须在寻优范围 "
                        + dto.minTableCount + " 到 " + dto.maxTableCount + " 之间！");
            }
            return;
        }

        if (dto.totalTables <= 0 || dto.totalTables > 200) {
            throw new IllegalArgumentException("桌子数量必须在 1 到 200 之间！");
        }
        if (dto.windowCount <= 0) {
            throw new IllegalArgumentException("窗口数量必须大于 0！");
        }
    }

    private void copyLockedOptimizationFields(SimulationConfigDTO dto) {
        dto.totalTables = presetDto.totalTables;
        dto.windowCount = presetDto.windowCount;
        dto.openDuration = presetDto.openDuration;
        dto.probSolo = presetDto.probSolo;
        dto.randomSeed = presetDto.randomSeed;
        dto.totalStudents = presetDto.totalStudents;
        dto.avgMealPrice = presetDto.avgMealPrice;
        dto.windowCostPerHour = presetDto.windowCostPerHour;
        dto.tableCost = presetDto.tableCost;
        dto.lostStudentPenalty = presetDto.lostStudentPenalty;
        dto.breakfastPopulationRatio = presetDto.breakfastPopulationRatio;
        dto.lunchPopulationRatio = presetDto.lunchPopulationRatio;
        dto.dinnerPopulationRatio = presetDto.dinnerPopulationRatio;
        dto.simulationMode = presetDto.simulationMode;
        dto.mealPeriod = presetDto.mealPeriod;
        dto.lockedFromOptimization = true;
        dto.lockedWindowDistances = cloneArray(presetDto.lockedWindowDistances);
        dto.lockedWindowAvgServeTime = cloneArray(presetDto.lockedWindowAvgServeTime);
        dto.minWindowCount = presetDto.minWindowCount;
        dto.maxWindowCount = presetDto.maxWindowCount;
        dto.minTableCount = presetDto.minTableCount;
        dto.maxTableCount = presetDto.maxTableCount;
    }

    private int[] cloneArray(int[] values) {
        return values == null ? null : values.clone();
    }

    // 提供给外部调用的方法：判断用户是点了确认还是取消
    public boolean isConfirmed() {
        return isConfirmed;
    }

    // 提供给外部调用的方法：获取打包好的数据
    public SimulationConfigDTO getConfigData() {
        return finalConfig;
    }
}
