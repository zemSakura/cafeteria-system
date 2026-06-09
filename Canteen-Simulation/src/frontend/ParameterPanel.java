package frontend;

import backend.config.CanteenConfig;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.JTextField;
import javax.swing.JDialog;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;

/**
 * Left-side control panel for the blue-white decision dashboard.
 * It only builds DTOs and delegates execution to MainDashboard.
 */
public class ParameterPanel extends JPanel {
    public interface Actions {
        void startSimulation(SimulationConfigDTO dto);
        void stopSimulation();
        void openOptimizer();
        void chooseOptimizationPreset();
        void clearPreset();
        void parametersChanged();
    }

    private final Actions actions;
    private final JTextField totalStudentsField = new JTextField(8);
    private final JTextField windowCountField = new JTextField(8);
    private final JTextField tableCountField = new JTextField(8);
    private final JTextField durationField = new JTextField(8);
    private final JTextField seedField = new JTextField(8);
    private final JCheckBox fixedSeedCheckBox = new JCheckBox("使用固定随机种子，保证结果可复现");
    private JPanel seedInputRow;
    private final JTextField soloProbField = new JTextField(8);
    private final JTextField mealPriceField = new JTextField(8);
    private final JTextField windowCostField = new JTextField(8);
    private final JTextField tableCostField = new JTextField(8);
    private final JTextField lostPenaltyField = new JTextField(8);
    private final JTextField minWindowField = new JTextField(8);
    private final JTextField maxWindowField = new JTextField(8);
    private final JTextField minTableField = new JTextField(8);
    private final JTextField maxTableField = new JTextField(8);
    private final JTextField currentWindowField = new JTextField(8);
    private final JTextField currentTableField = new JTextField(8);
    private final JTextField repeatTimesField = new JTextField(8);
    private final JTextField maxIterationsField = new JTextField(8);
    private final JTextField restartCountField = new JTextField(8);
    private final JTextField topKField = new JTextField(8);
    private final JTextField breakfastRatioField = new JTextField(8);
    private final JTextField lunchRatioField = new JTextField(8);
    private final JTextField dinnerRatioField = new JTextField(8);
    private final JTextField waitWeightField = new JTextField(8);
    private final JTextField seatWaitWeightField = new JTextField(8);
    private final JTextField queueWeightField = new JTextField(8);
    private final JTextField abandonWeightField = new JTextField(8);
    private final JTextField crowdingWeightField = new JTextField(8);
    private final JTextField minFinishRateField = new JTextField(8);
    private final JTextField hardWaitField = new JTextField(8);
    private final JTextField hardSeatWaitField = new JTextField(8);
    private final JTextField hardQueueField = new JTextField(8);
    private final JTextField hardAbandonField = new JTextField(8);
    private final JComboBox<String> optimizationModeBox = new JComboBox<>(
            new String[]{"均衡模式", "收益优先", "完成率优先", "体验优先"});
    private final JComboBox<String> modeBox = new JComboBox<>(new String[]{"单时段仿真", "全天连续仿真"});
    private final JComboBox<String> mealBox = new JComboBox<>(new String[]{"早餐", "午餐", "晚餐"});
    private final JButton settingsButton = new JButton("参数设置");
    private final JButton startButton = new JButton("开始仿真");
    private final JButton optimizeButton = new JButton("启动寻优");
    private final JButton choosePresetButton = new JButton("从寻优范围选择");
    private final JButton clearPresetButton = new JButton("恢复自定义");
    private final JButton stopButton = new JButton("停止仿真");
    private final JButton resetButton = new JButton("重置参数");
    private final JLabel phaseLabel = new JLabel("等待运行");
    private final JLabel presetLabel = new JLabel("自定义参数");
    private final JLabel summaryStudents = new JLabel();
    private final JLabel summaryDuration = new JLabel();
    private final JLabel summaryResource = new JLabel();
    private final JLabel summaryMode = new JLabel();
    private final JLabel summaryRange = new JLabel();
    private SimulationConfigDTO lockedPreset;
    private boolean suppressParameterChangeEvents;

    public ParameterPanel(Actions actions) {
        super(new BorderLayout(0, 12));
        this.actions = actions;
        setPreferredSize(new Dimension(380, 0));
        setMinimumSize(new Dimension(350, 0));
        setBackground(ColorTheme.BG_MAIN);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel card = new JPanel(new BorderLayout(0, 14));
        card.setBackground(ColorTheme.BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)
        ));
        add(card, BorderLayout.CENTER);

        installSeedInputGuard();
        wireSeedControls();
        optimizationModeBox.setToolTipText("收益权重由优化目标自动决定；收益优先模式会优先比较净收益。");
        resetDefaults();
        card.add(createHeader(), BorderLayout.NORTH);
        card.add(createQuickSummary(), BorderLayout.CENTER);
        card.add(createActions(), BorderLayout.SOUTH);

        wireActions();
        installChangeTracking();
    }

    public JButton getStartButton() {
        return startButton;
    }

    public JButton getStopButton() {
        return stopButton;
    }

    public JButton getChoosePresetButton() {
        return choosePresetButton;
    }

    public JButton getClearPresetButton() {
        return clearPresetButton;
    }

    public JLabel getPhaseLabel() {
        return phaseLabel;
    }

    public void applyPreset(SimulationConfigDTO dto) {
        lockedPreset = copy(dto);
        suppressParameterChangeEvents = true;
        try {
            setFieldsFrom(dto);
        } finally {
            suppressParameterChangeEvents = false;
        }
        presetLabel.setText("已导入寻优方案");
        presetLabel.setForeground(ColorTheme.ACCENT_BLUE);
        clearPresetButton.setEnabled(true);
    }

    public void clearPresetState() {
        lockedPreset = null;
        presetLabel.setText("自定义参数");
        presetLabel.setForeground(ColorTheme.TEXT_SECONDARY);
        clearPresetButton.setEnabled(false);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setOpaque(false);

        JLabel title = new JLabel("控制面板");
        title.setForeground(ColorTheme.TEXT_PRIMARY);
        title.setFont(ColorTheme.font(Font.BOLD, 20));
        header.add(title, BorderLayout.NORTH);

        JPanel statusPanel = new JPanel(new GridLayout(0, 1, 0, 6));
        statusPanel.setOpaque(false);
        phaseLabel.setOpaque(true);
        phaseLabel.setBackground(ColorTheme.BG_ITEM);
        phaseLabel.setForeground(ColorTheme.TEXT_SECONDARY);
        phaseLabel.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        phaseLabel.setFont(ColorTheme.font(Font.BOLD, 14));
        presetLabel.setForeground(ColorTheme.TEXT_SECONDARY);
        presetLabel.setFont(ColorTheme.font(Font.PLAIN, 13));
        statusPanel.add(phaseLabel);
        statusPanel.add(presetLabel);
        header.add(statusPanel, BorderLayout.CENTER);
        return header;
    }

    private JPanel createQuickSummary() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        JPanel form = new WidthTrackingPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.insets = new Insets(0, 0, 8, 0);

        addCompactRow(form, gbc, "预计就餐人数", totalStudentsField);
        addCompactRow(form, gbc, "仿真时长", durationField);
        addCompactRow(form, gbc, "当前窗口数", windowCountField);
        addCompactRow(form, gbc, "当前餐桌数", tableCountField);
        addCompactRow(form, gbc, "窗口数量范围", createRangePanel(minWindowField, maxWindowField));
        addCompactRow(form, gbc, "餐桌数量范围", createRangePanel(minTableField, maxTableField));
        addCompactRow(form, gbc, "平均客单价", mealPriceField);
        addCompactRow(form, gbc, "优化目标", optimizationModeBox);

        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        wrapper.add(scrollPane, BorderLayout.CENTER);
        return wrapper;
    }

    private void addCompactRow(JPanel form, GridBagConstraints gbc, String label, java.awt.Component field) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(true);
        row.setBackground(ColorTheme.BG_ITEM);
        row.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        JLabel labelView = new JLabel(label);
        labelView.setForeground(ColorTheme.TEXT_SECONDARY);
        labelView.setFont(ColorTheme.font(Font.PLAIN, 12));
        labelView.setPreferredSize(new Dimension(92, 24));
        row.add(labelView, BorderLayout.WEST);
        styleCompactControl(field);
        row.add(field, BorderLayout.CENTER);
        form.add(row, gbc);
        gbc.gridy++;
    }

    private JPanel createRangePanel(JTextField minField, JTextField maxField) {
        JPanel panel = new JPanel(new GridLayout(1, 3, 6, 0));
        panel.setOpaque(false);
        styleCompactControl(minField);
        styleCompactControl(maxField);
        JLabel separator = new JLabel("至", SwingConstants.CENTER);
        separator.setForeground(ColorTheme.TEXT_SECONDARY);
        separator.setFont(ColorTheme.font(Font.PLAIN, 12));
        panel.add(minField);
        panel.add(separator);
        panel.add(maxField);
        return panel;
    }

    private void styleCompactControl(java.awt.Component field) {
        field.setFont(ColorTheme.font(Font.PLAIN, 13));
        field.setPreferredSize(new Dimension(0, 30));
        if (field instanceof JTextField) {
            JTextField textField = (JTextField) field;
            textField.setHorizontalAlignment(SwingConstants.CENTER);
            textField.setBackground(Color.WHITE);
            textField.setForeground(ColorTheme.TEXT_PRIMARY);
            textField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)
            ));
        }
    }

    private JPanel createActions() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 8));
        panel.setOpaque(false);
        styleSecondary(settingsButton);
        stylePrimary(startButton);
        styleSecondary(optimizeButton);
        styleSecondary(choosePresetButton);
        styleSecondary(clearPresetButton);
        styleSecondary(resetButton);
        styleDanger(stopButton);
        choosePresetButton.setEnabled(false);
        clearPresetButton.setEnabled(false);
        stopButton.setEnabled(false);
        stopButton.setVisible(false);
        panel.add(settingsButton);
        panel.add(startButton);
        panel.add(optimizeButton);
        panel.add(choosePresetButton);
        panel.add(clearPresetButton);
        panel.add(resetButton);
        panel.add(stopButton);
        return panel;
    }

    private void wireActions() {
        modeBox.addActionListener(e -> {
            mealBox.setEnabled(modeBox.getSelectedIndex() == 0);
            refreshSummary();
            notifyParametersChanged();
        });
        mealBox.addActionListener(e -> {
            refreshSummary();
            notifyParametersChanged();
        });
        optimizationModeBox.addActionListener(e -> {
            refreshSummary();
            notifyParametersChanged();
        });
        settingsButton.addActionListener(e -> {
            try {
                syncCommonOptimizationSettings();
                showAdvancedSettingsDialog(null);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "参数错误", JOptionPane.ERROR_MESSAGE);
            }
        });
        startButton.addActionListener(e -> {
            try {
                actions.startSimulation(buildConfig());
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "参数错误", JOptionPane.ERROR_MESSAGE);
            }
        });
        stopButton.addActionListener(e -> {
            actions.stopSimulation();
            stopButton.setVisible(false);
        });
        optimizeButton.addActionListener(e -> {
            try {
                syncCommonOptimizationSettings();
                refreshSummary();
                actions.openOptimizer();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "参数错误", JOptionPane.ERROR_MESSAGE);
            }
        });
        choosePresetButton.addActionListener(e -> actions.chooseOptimizationPreset());
        clearPresetButton.addActionListener(e -> actions.clearPreset());
        resetButton.addActionListener(e -> {
            resetDefaults();
            actions.clearPreset();
            notifyParametersChanged();
        });
    }

    private void installChangeTracking() {
        JTextField[] fields = {
                totalStudentsField, durationField, windowCountField, tableCountField,
                minWindowField, maxWindowField, minTableField, maxTableField,
                mealPriceField, windowCostField, tableCostField
        };
        for (JTextField field : fields) {
            field.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    changed();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    changed();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    changed();
                }

                private void changed() {
                    refreshSummary();
                    notifyParametersChanged();
                }
            });
        }
    }

    private void notifyParametersChanged() {
        if (suppressParameterChangeEvents) {
            return;
        }
        if (lockedPreset != null) {
            clearPresetState();
        }
        actions.parametersChanged();
    }

    private void showAdvancedSettingsDialog(JDialog owner) {
        suppressParameterChangeEvents = true;
        try {
            setAdvancedFieldsFromSettings();
        } finally {
            suppressParameterChangeEvents = false;
        }
        JDialog dialog = new JDialog(owner, "高级设置", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(0, 12));
        dialog.getContentPane().setBackground(ColorTheme.BG_MAIN);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(ColorTheme.BG_CARD);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)
        ));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 8, 0);
        form.add(createInfoBox("以下参数用于实验和调试，普通用户无需修改，系统已提供默认值。"), gbc);

        gbc.gridy++;
        form.add(sectionTitle("复现实验"), gbc);
        gbc.gridy++;
        form.add(createInfoBox("随机种子用于复现实验结果，普通用户无需修改。未勾选时系统会自动生成随机种子。"), gbc);
        gbc.gridy++;
        styleSeedCheckBox();
        form.add(fixedSeedCheckBox, gbc);
        gbc.gridy++;
        seedInputRow = createInlineFieldRow("固定种子值", seedField,
                "只允许 long 范围内的整数；留空时系统自动生成随机种子。");
        form.add(seedInputRow, gbc);
        updateSeedInputVisibility();
        gbc.gridwidth = 1;
        addRow(form, gbc, "单人概率", soloProbField);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(12, 0, 8, 0);
        form.add(sectionTitle("经营成本"), gbc);
        gbc.gridwidth = 1;
        addRow(form, gbc, "窗口小时成本", windowCostField);
        addRow(form, gbc, "单桌成本", tableCostField);
        addRow(form, gbc, "放弃损失", lostPenaltyField);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(12, 0, 8, 0);
        form.add(sectionTitle("全天人数分布"), gbc);
        gbc.gridwidth = 1;
        addRow(form, gbc, "早餐比例", breakfastRatioField);
        addRow(form, gbc, "午餐比例", lunchRatioField);
        addRow(form, gbc, "晚餐比例", dinnerRatioField);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(12, 0, 8, 0);
        form.add(sectionTitle("寻优高级参数"), gbc);
        gbc.gridwidth = 1;
        addRow(form, gbc, "重复次数", repeatTimesField);
        addRow(form, gbc, "候选上限", maxIterationsField);
        addRow(form, gbc, "多起点次数", restartCountField);
        addRow(form, gbc, "TopK 数量", topKField);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(12, 0, 8, 0);
        form.add(sectionTitle("惩罚权重"), gbc);
        gbc.gridwidth = 1;
        addRow(form, gbc, "等待权重", waitWeightField,
                "越大，系统越倾向于减少窗口排队时间。");
        addRow(form, gbc, "等座权重", seatWaitWeightField,
                "越大，系统越倾向于减少找座等待时间。");
        addRow(form, gbc, "排队权重", queueWeightField,
                "越大，系统越倾向于压低窗口队列峰值。");
        addRow(form, gbc, "放弃权重", abandonWeightField,
                "越大，系统越倾向于减少放弃就餐人数。");
        addRow(form, gbc, "拥挤权重", crowdingWeightField,
                "越大，系统越倾向于降低座位拥挤程度。");
        addRow(form, gbc, "最低完成率", minFinishRateField,
                "低于该完成率时，评价函数会追加完成率不足惩罚。");
        addRow(form, gbc, "P95等待阈值", hardWaitField,
                "用于判断大多数学生可接受的窗口排队等待上限。");
        addRow(form, gbc, "P95等座阈值", hardSeatWaitField,
                "用于判断大多数学生可接受的等座等待上限。");
        addRow(form, gbc, "最大队列阈值", hardQueueField,
                "超过该峰值队列长度时，会触发二次惩罚。");
        addRow(form, gbc, "放弃率阈值", hardAbandonField,
                "超过该放弃率时，会触发二次惩罚。");

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(0, 0, 8, 0);
        form.add(createInfoBox("搜索上下限留空时，系统按 ABES 从小范围起步并在贴边时扩展；三餐比例会自动归一化。"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(8, 0, 8, 0);
        form.add(createLossFunctionInfoBox(), gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        JButton cancel = new JButton("取消");
        JButton save = new JButton("保存");
        styleSecondary(cancel);
        stylePrimary(save);
        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            try {
                parseDouble(soloProbField, "单人概率", 0.0, 1.0);
                saveAdvancedSettingsFromFields(dialog);
                refreshSummary();
                notifyParametersChanged();
                dialog.dispose();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "参数错误", JOptionPane.ERROR_MESSAGE);
            }
        });
        buttons.add(cancel);
        buttons.add(save);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(ColorTheme.BG_MAIN);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.add(content, BorderLayout.CENTER);
        dialog.setSize(620, 720);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private SimulationConfigDTO buildConfig() {
        syncCommonOptimizationSettings();
        SimulationConfigDTO dto = lockedPreset == null ? new SimulationConfigDTO() : copy(lockedPreset);
        dto.totalStudents = parseInt(totalStudentsField, "就餐人数", 1, 1_000_000);
        dto.openDuration = parseInt(durationField, "开放时长", 1, 24 * 60);
        dto.windowCount = parseInt(windowCountField, "窗口数", 1, 200);
        dto.totalTables = parseInt(tableCountField, "桌子数", 1, 10_000);
        dto.randomSeed = lockedPreset == null ? resolveRandomSeed(this) : lockedPreset.randomSeed;
        dto.probSolo = parseDouble(soloProbField, "单人概率", 0.0, 1.0);
        dto.avgMealPrice = AdvancedOptimizationSettings.avgMealPrice;
        dto.windowCostPerHour = AdvancedOptimizationSettings.windowCostPerHour;
        dto.tableCost = AdvancedOptimizationSettings.tableCost;
        dto.lostStudentPenalty = AdvancedOptimizationSettings.lostStudentPenalty;
        dto.breakfastPopulationRatio = AdvancedOptimizationSettings.breakfastPopulationRatio;
        dto.lunchPopulationRatio = AdvancedOptimizationSettings.lunchPopulationRatio;
        dto.dinnerPopulationRatio = AdvancedOptimizationSettings.dinnerPopulationRatio;
        dto.simulationMode = modeBox.getSelectedIndex() == 1 ? "fullDay" : "singlePeriod";
        dto.mealPeriod = mealCode();
        dto.minWindowCount = AdvancedOptimizationSettings.minWindowCount;
        dto.maxWindowCount = AdvancedOptimizationSettings.maxWindowCount;
        dto.minTableCount = AdvancedOptimizationSettings.minTableCount;
        dto.maxTableCount = AdvancedOptimizationSettings.maxTableCount;
        dto.lockedFromOptimization = lockedPreset != null;
        if (dto.lockedFromOptimization
                && "singlePeriod".equals(dto.simulationMode)
                && "fullDay".equals(lockedPreset.simulationMode)) {
            dto.totalStudents = AdvancedOptimizationSettings.populationForPeriod(dto.totalStudents, dto.mealPeriod);
        }
        return dto;
    }

    private void resetDefaults() {
        suppressParameterChangeEvents = true;
        try {
            AdvancedOptimizationSettings.resetDefaults();
            setAdvancedFieldsFromSettings();
            SimulationConfigDTO dto = new SimulationConfigDTO();
            dto.simulationMode = "fullDay";
            dto.mealPeriod = "lunch";
            dto.breakfastPopulationRatio = AdvancedOptimizationSettings.breakfastPopulationRatio;
            dto.lunchPopulationRatio = AdvancedOptimizationSettings.lunchPopulationRatio;
            dto.dinnerPopulationRatio = AdvancedOptimizationSettings.dinnerPopulationRatio;
            dto.minWindowCount = 0;
            dto.maxWindowCount = 0;
            dto.minTableCount = 0;
            dto.maxTableCount = 0;
            setFieldsFrom(dto);
            clearPresetState();
            phaseLabel.setText("等待运行");
            refreshSummary();
        } finally {
            suppressParameterChangeEvents = false;
        }
    }

    private void setFieldsFrom(SimulationConfigDTO dto) {
        totalStudentsField.setText(String.valueOf(dto.totalStudents));
        durationField.setText(String.valueOf(dto.openDuration));
        windowCountField.setText(String.valueOf(dto.windowCount));
        tableCountField.setText(String.valueOf(dto.totalTables));
        seedField.setText(String.valueOf(dto.randomSeed));
        soloProbField.setText(String.valueOf(dto.probSolo));
        mealPriceField.setText(String.valueOf(dto.avgMealPrice));
        windowCostField.setText(String.valueOf(dto.windowCostPerHour));
        tableCostField.setText(String.valueOf(dto.tableCost));
        lostPenaltyField.setText(String.valueOf(dto.lostStudentPenalty));
        minWindowField.setText(optionalIntText(dto.minWindowCount));
        maxWindowField.setText(optionalIntText(dto.maxWindowCount));
        minTableField.setText(optionalIntText(dto.minTableCount));
        maxTableField.setText(optionalIntText(dto.maxTableCount));
        modeBox.setSelectedIndex("fullDay".equals(dto.simulationMode) ? 1 : 0);
        mealBox.setSelectedIndex(mealIndex(dto.mealPeriod));
        mealBox.setEnabled(modeBox.getSelectedIndex() == 0);
        refreshSummary();
    }

    private void refreshSummary() {
        summaryStudents.setText(totalStudentsField.getText() + " 人");
        summaryDuration.setText(durationField.getText() + " 分钟");
        summaryResource.setText(windowCountField.getText() + " 窗 / " + tableCountField.getText() + " 桌");
        summaryMode.setText(modeBox.getSelectedIndex() == 1
                ? "全天三餐"
                : "单时段 / " + mealBox.getSelectedItem());
        summaryRange.setText(formatSearchRangeSummary());
    }

    private void addRow(JPanel form, GridBagConstraints gbc, String label, java.awt.Component field) {
        addRow(form, gbc, label, field, null);
    }

    private void addRow(JPanel form, GridBagConstraints gbc, String label,
                        java.awt.Component field, String tooltip) {
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.insets = new Insets(0, 0, 8, 8);
        JLabel labelView = new JLabel(label);
        labelView.setForeground(ColorTheme.TEXT_SECONDARY);
        labelView.setFont(ColorTheme.font(Font.PLAIN, 13));
        if (tooltip != null && !tooltip.isEmpty()) {
            labelView.setToolTipText(tooltip);
            if (field instanceof javax.swing.JComponent) {
                ((javax.swing.JComponent) field).setToolTipText(tooltip);
            }
        }
        form.add(labelView, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.insets = new Insets(0, 0, 8, 0);
        field.setFont(ColorTheme.font(Font.PLAIN, 14));
        field.setPreferredSize(new Dimension(300, 36));
        if (field instanceof JTextField) {
            ((JTextField) field).setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT),
                    BorderFactory.createEmptyBorder(5, 8, 5, 8)
            ));
        }
        form.add(field, gbc);
    }

    private JPanel createInlineFieldRow(String label, java.awt.Component field, String tooltip) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel labelView = new JLabel(label);
        labelView.setForeground(ColorTheme.TEXT_SECONDARY);
        labelView.setFont(ColorTheme.font(Font.PLAIN, 13));
        labelView.setPreferredSize(new Dimension(96, 34));
        if (tooltip != null && !tooltip.isEmpty()) {
            labelView.setToolTipText(tooltip);
            if (field instanceof javax.swing.JComponent) {
                ((javax.swing.JComponent) field).setToolTipText(tooltip);
            }
        }
        field.setFont(ColorTheme.font(Font.PLAIN, 14));
        field.setPreferredSize(new Dimension(300, 36));
        if (field instanceof JTextField) {
            ((JTextField) field).setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT),
                    BorderFactory.createEmptyBorder(5, 8, 5, 8)
            ));
        }
        row.add(labelView, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(ColorTheme.ACCENT_BLUE);
        label.setFont(ColorTheme.font(Font.BOLD, 14));
        return label;
    }

    private JLabel createInfoBox(String text) {
        return createInfoBox(text, 270);
    }

    private JLabel createInfoBox(String text, int width) {
        JLabel label = new JLabel("<html><body style='width:" + width + "px; line-height:1.35'>" + text + "</body></html>");
        label.setOpaque(true);
        label.setBackground(ColorTheme.BG_ITEM);
        label.setForeground(ColorTheme.TEXT_SECONDARY);
        label.setFont(ColorTheme.font(Font.PLAIN, 12));
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorTheme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        return label;
    }

    private JLabel createLossFunctionInfoBox() {
        return createInfoBox(
                "<b>评价函数说明</b><br>"
                        + "综合损失 Loss = 成本损失 + 体验损失 + 硬阈值惩罚 + 完成率不足惩罚。<br>"
                        + "成本损失 = 成本权重 × (窗口成本×窗口数 + 桌子成本×桌数) / 可接受成本上限。<br>"
                        + "体验损失 = 体验总权重 × (排队等待权重×P95排队等待/可接受等待上限 "
                        + "+ 等座等待权重×P95等座等待/可接受等待上限 "
                        + "+ 队列长度权重×最大队列长度/可接受队列上限 "
                        + "+ 放弃惩罚权重×放弃率/可接受放弃率 "
                        + "+ 拥挤惩罚权重×超过舒适座位利用率的部分/拥挤缩放)。<br>"
                        + "P95 无统计值时使用平均等待；体验子权重会先归一化到合计 1。<br>"
                        + "硬阈值惩罚会对 P95 排队等待、P95 等座等待、最大队列、放弃率、座位利用率的超限部分做二次惩罚。<br>"
                        + "Score = 模式收益权重×净收益评分 + 模式完成率权重×完成率 - 模式等待权重×等待惩罚 - 模式放弃权重×放弃惩罚。<br>"
                        + "Loss 越低越好，Score 越高越好。收益优先、完成率优先、体验优先、均衡模式会使用不同排序规则；收益权重由优化目标自动设置，不是单独输入项。",
                520
        );
    }

    private void styleSeedCheckBox() {
        fixedSeedCheckBox.setOpaque(false);
        fixedSeedCheckBox.setForeground(ColorTheme.TEXT_PRIMARY);
        fixedSeedCheckBox.setFont(ColorTheme.font(Font.PLAIN, 13));
        fixedSeedCheckBox.setToolTipText("勾选后使用同一个整数种子，便于复现实验结果。");
    }

    private void wireSeedControls() {
        fixedSeedCheckBox.addActionListener(event -> updateSeedInputVisibility());
    }

    private void updateSeedInputVisibility() {
        boolean fixed = fixedSeedCheckBox.isSelected();
        seedField.setEnabled(fixed);
        seedField.setEditable(fixed);
        if (seedInputRow != null) {
            seedInputRow.setVisible(fixed);
            seedInputRow.revalidate();
            seedInputRow.repaint();
        }
    }

    private void installSeedInputGuard() {
        ((AbstractDocument) seedField.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                    throws BadLocationException {
                replace(fb, offset, 0, string, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                    throws BadLocationException {
                String current = fb.getDocument().getText(0, fb.getDocument().getLength());
                StringBuilder builder = new StringBuilder(current);
                builder.replace(offset, offset + length, text == null ? "" : text);
                if (isSeedTextAllowed(builder.toString())) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }

            @Override
            public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                replace(fb, offset, length, "", null);
            }
        });
    }

    private boolean isSeedTextAllowed(String text) {
        return text == null || text.isEmpty() || "-".equals(text) || text.matches("-?\\d+");
    }

    private void saveRandomSeedSettings(java.awt.Component owner) {
        if (!fixedSeedCheckBox.isSelected()) {
            AdvancedOptimizationSettings.useFixedRandomSeed = false;
            return;
        }
        String text = seedField.getText().trim();
        if (text.isEmpty()) {
            AdvancedOptimizationSettings.useFixedRandomSeed = false;
            return;
        }
        try {
            AdvancedOptimizationSettings.fixedRandomSeed = Long.parseLong(text);
            AdvancedOptimizationSettings.useFixedRandomSeed = true;
        } catch (NumberFormatException ex) {
            showSeedFallbackMessage(owner);
            AdvancedOptimizationSettings.fixedRandomSeed = CanteenConfig.DEFAULT_RANDOM_SEED;
            AdvancedOptimizationSettings.useFixedRandomSeed = false;
            fixedSeedCheckBox.setSelected(false);
            seedField.setText("");
            updateSeedInputVisibility();
        }
    }

    private long resolveRandomSeed(java.awt.Component owner) {
        if (!AdvancedOptimizationSettings.useFixedRandomSeed) {
            return AdvancedOptimizationSettings.nextAutomaticRandomSeed();
        }
        String text = seedField.getText().trim();
        if (text.isEmpty()) {
            AdvancedOptimizationSettings.useFixedRandomSeed = false;
            return AdvancedOptimizationSettings.nextAutomaticRandomSeed();
        }
        try {
            long seed = Long.parseLong(text);
            AdvancedOptimizationSettings.fixedRandomSeed = seed;
            return seed;
        } catch (NumberFormatException ex) {
            showSeedFallbackMessage(owner);
            AdvancedOptimizationSettings.fixedRandomSeed = CanteenConfig.DEFAULT_RANDOM_SEED;
            AdvancedOptimizationSettings.useFixedRandomSeed = false;
            fixedSeedCheckBox.setSelected(false);
            seedField.setText("");
            updateSeedInputVisibility();
            return CanteenConfig.DEFAULT_RANDOM_SEED;
        }
    }

    private void showSeedFallbackMessage(java.awt.Component owner) {
        JOptionPane.showMessageDialog(
                owner,
                "随机种子必须为整数，已自动使用系统默认种子。",
                "随机种子格式错误",
                JOptionPane.WARNING_MESSAGE
        );
    }

    private void stylePrimary(JButton button) {
        styleButton(button, ColorTheme.ACCENT_CYAN, Color.WHITE);
    }

    private void styleSecondary(JButton button) {
        styleButton(button, ColorTheme.BG_CONTROL, ColorTheme.ACCENT_BLUE);
    }

    private void styleDanger(JButton button) {
        styleButton(button, new Color(254, 226, 226), ColorTheme.ACCENT_RED);
    }

    private void styleButton(JButton button, Color bg, Color fg) {
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setBackground(bg);
        button.setForeground(fg);
        button.setFont(ColorTheme.font(Font.BOLD, 14));
        button.setLayout(new FlowLayout(FlowLayout.CENTER));
        button.putClientProperty(
                "FlatLaf.style",
                "arc: 16; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; margin: 8,14,8,14"
        );
    }

    private void setAdvancedFieldsFromSettings() {
        fixedSeedCheckBox.setSelected(AdvancedOptimizationSettings.useFixedRandomSeed);
        seedField.setText(AdvancedOptimizationSettings.useFixedRandomSeed
                ? String.valueOf(AdvancedOptimizationSettings.fixedRandomSeed)
                : "");
        updateSeedInputVisibility();
        mealPriceField.setText(String.valueOf(AdvancedOptimizationSettings.avgMealPrice));
        windowCostField.setText(String.valueOf(AdvancedOptimizationSettings.windowCostPerHour));
        tableCostField.setText(String.valueOf(AdvancedOptimizationSettings.tableCost));
        lostPenaltyField.setText(String.valueOf(AdvancedOptimizationSettings.lostStudentPenalty));
        breakfastRatioField.setText(String.valueOf(AdvancedOptimizationSettings.breakfastPopulationRatio));
        lunchRatioField.setText(String.valueOf(AdvancedOptimizationSettings.lunchPopulationRatio));
        dinnerRatioField.setText(String.valueOf(AdvancedOptimizationSettings.dinnerPopulationRatio));
        minWindowField.setText(optionalIntText(AdvancedOptimizationSettings.minWindowCount));
        maxWindowField.setText(optionalIntText(AdvancedOptimizationSettings.maxWindowCount));
        minTableField.setText(optionalIntText(AdvancedOptimizationSettings.minTableCount));
        maxTableField.setText(optionalIntText(AdvancedOptimizationSettings.maxTableCount));
        currentWindowField.setText(optionalIntText(AdvancedOptimizationSettings.currentWindowCount));
        currentTableField.setText(optionalIntText(AdvancedOptimizationSettings.currentTableCount));
        repeatTimesField.setText(String.valueOf(AdvancedOptimizationSettings.repeatTimes));
        maxIterationsField.setText(String.valueOf(AdvancedOptimizationSettings.maxCandidateEvaluations));
        restartCountField.setText(String.valueOf(AdvancedOptimizationSettings.localRestartCount));
        topKField.setText(String.valueOf(AdvancedOptimizationSettings.topK));
        optimizationModeBox.setSelectedItem(AdvancedOptimizationSettings.optimizationModeDisplayName);
        waitWeightField.setText(String.valueOf(AdvancedOptimizationSettings.waitWeight));
        seatWaitWeightField.setText(String.valueOf(AdvancedOptimizationSettings.seatWaitWeight));
        queueWeightField.setText(String.valueOf(AdvancedOptimizationSettings.queueWeight));
        abandonWeightField.setText(String.valueOf(AdvancedOptimizationSettings.abandonWeight));
        crowdingWeightField.setText(String.valueOf(AdvancedOptimizationSettings.crowdingWeight));
        minFinishRateField.setText(String.valueOf(AdvancedOptimizationSettings.minAcceptFinishRate));
        hardWaitField.setText(String.valueOf(AdvancedOptimizationSettings.hardWaitThresholdMinutes));
        hardSeatWaitField.setText(String.valueOf(AdvancedOptimizationSettings.hardSeatWaitThresholdMinutes));
        hardQueueField.setText(String.valueOf(AdvancedOptimizationSettings.hardQueueThresholdLength));
        hardAbandonField.setText(String.valueOf(AdvancedOptimizationSettings.hardAbandonThresholdRate));
    }

    private void saveAdvancedSettingsFromFields(java.awt.Component owner) {
        saveRandomSeedSettings(owner);
        AdvancedOptimizationSettings.avgMealPrice = parseDouble(mealPriceField, "平均客单价", 0.0, 10_000.0);
        AdvancedOptimizationSettings.windowCostPerHour = parseDouble(windowCostField, "窗口小时成本", 0.0, 10_000.0);
        AdvancedOptimizationSettings.tableCost = parseDouble(tableCostField, "单桌成本", 0.0, 10_000.0);
        AdvancedOptimizationSettings.lostStudentPenalty = parseDouble(lostPenaltyField, "放弃损失", 0.0, 10_000.0);
        AdvancedOptimizationSettings.breakfastPopulationRatio = parseDouble(breakfastRatioField, "早餐比例", 0.0, 1.0);
        AdvancedOptimizationSettings.lunchPopulationRatio = parseDouble(lunchRatioField, "午餐比例", 0.0, 1.0);
        AdvancedOptimizationSettings.dinnerPopulationRatio = parseDouble(dinnerRatioField, "晚餐比例", 0.0, 1.0);
        if (AdvancedOptimizationSettings.breakfastPopulationRatio
                + AdvancedOptimizationSettings.lunchPopulationRatio
                + AdvancedOptimizationSettings.dinnerPopulationRatio <= 0.0) {
            throw new IllegalArgumentException("早中晚人数比例之和必须大于 0。");
        }
        AdvancedOptimizationSettings.minWindowCount = parseOptionalInt(minWindowField, "窗口下限", 1, 200);
        AdvancedOptimizationSettings.maxWindowCount = parseOptionalInt(maxWindowField, "窗口上限", 1, 200);
        AdvancedOptimizationSettings.minTableCount = parseOptionalInt(minTableField, "桌子下限", 1, 10_000);
        AdvancedOptimizationSettings.maxTableCount = parseOptionalInt(maxTableField, "桌子上限", 1, 10_000);
        if (AdvancedOptimizationSettings.minWindowCount > 0
                && AdvancedOptimizationSettings.maxWindowCount > 0
                && AdvancedOptimizationSettings.maxWindowCount < AdvancedOptimizationSettings.minWindowCount) {
            throw new IllegalArgumentException("窗口上限不能小于窗口下限。");
        }
        if (AdvancedOptimizationSettings.minTableCount > 0
                && AdvancedOptimizationSettings.maxTableCount > 0
                && AdvancedOptimizationSettings.maxTableCount < AdvancedOptimizationSettings.minTableCount) {
            throw new IllegalArgumentException("桌子上限不能小于桌子下限。");
        }
        AdvancedOptimizationSettings.currentWindowCount = parseInt(windowCountField, "当前窗口", 1, 200);
        AdvancedOptimizationSettings.currentTableCount = parseInt(tableCountField, "当前桌子", 1, 10_000);
        AdvancedOptimizationSettings.repeatTimes = parseInt(repeatTimesField, "重复次数", 1, 100);
        AdvancedOptimizationSettings.maxCandidateEvaluations = parseInt(maxIterationsField, "候选上限", 1, 10_000);
        AdvancedOptimizationSettings.localRestartCount = parseInt(restartCountField, "多起点次数", 1, 100);
        AdvancedOptimizationSettings.topK = parseInt(topKField, "TopK 数量", 1, 200);
        AdvancedOptimizationSettings.optimizationModeDisplayName = (String) optimizationModeBox.getSelectedItem();
        AdvancedOptimizationSettings.waitWeight = parseDouble(waitWeightField, "等待权重", 0.0, 100.0);
        AdvancedOptimizationSettings.seatWaitWeight = parseDouble(seatWaitWeightField, "等座权重", 0.0, 100.0);
        AdvancedOptimizationSettings.queueWeight = parseDouble(queueWeightField, "排队权重", 0.0, 100.0);
        AdvancedOptimizationSettings.abandonWeight = parseDouble(abandonWeightField, "放弃权重", 0.0, 100.0);
        AdvancedOptimizationSettings.crowdingWeight = parseDouble(crowdingWeightField, "拥挤权重", 0.0, 100.0);
        AdvancedOptimizationSettings.minAcceptFinishRate = parseDouble(minFinishRateField, "最低完成率", 0.0, 1.0);
        AdvancedOptimizationSettings.hardWaitThresholdMinutes = parseDouble(hardWaitField, "P95等待阈值", 0.1, 24 * 60.0);
        AdvancedOptimizationSettings.hardSeatWaitThresholdMinutes = parseDouble(hardSeatWaitField, "P95等座阈值", 0.1, 24 * 60.0);
        AdvancedOptimizationSettings.hardQueueThresholdLength = parseDouble(hardQueueField, "最大队列阈值", 1.0, 1_000_000.0);
        AdvancedOptimizationSettings.hardAbandonThresholdRate = parseDouble(hardAbandonField, "放弃率阈值", 0.001, 1.0);
        syncCommonOptimizationSettings();
        AdvancedOptimizationSettings.applyPopulationRatiosToRuntime();
    }

    private void syncCommonOptimizationSettings() {
        AdvancedOptimizationSettings.avgMealPrice = parseDouble(mealPriceField, "平均客单价", 0.0, 10_000.0);
        AdvancedOptimizationSettings.minWindowCount = parseOptionalInt(minWindowField, "窗口下限", 1, 200);
        AdvancedOptimizationSettings.maxWindowCount = parseOptionalInt(maxWindowField, "窗口上限", 1, 200);
        AdvancedOptimizationSettings.minTableCount = parseOptionalInt(minTableField, "桌子下限", 1, 10_000);
        AdvancedOptimizationSettings.maxTableCount = parseOptionalInt(maxTableField, "桌子上限", 1, 10_000);
        if (AdvancedOptimizationSettings.minWindowCount > 0
                && AdvancedOptimizationSettings.maxWindowCount > 0
                && AdvancedOptimizationSettings.maxWindowCount < AdvancedOptimizationSettings.minWindowCount) {
            throw new IllegalArgumentException("窗口上限不能小于窗口下限。");
        }
        if (AdvancedOptimizationSettings.minTableCount > 0
                && AdvancedOptimizationSettings.maxTableCount > 0
                && AdvancedOptimizationSettings.maxTableCount < AdvancedOptimizationSettings.minTableCount) {
            throw new IllegalArgumentException("桌子上限不能小于桌子下限。");
        }
        AdvancedOptimizationSettings.currentWindowCount = parseInt(windowCountField, "当前窗口", 1, 200);
        AdvancedOptimizationSettings.currentTableCount = parseInt(tableCountField, "当前桌子", 1, 10_000);
        AdvancedOptimizationSettings.optimizationModeDisplayName = (String) optimizationModeBox.getSelectedItem();
    }

    private String formatSearchRangeSummary() {
        String window = formatOptionalRange(
                AdvancedOptimizationSettings.minWindowCount,
                AdvancedOptimizationSettings.maxWindowCount,
                "窗");
        String table = formatOptionalRange(
                AdvancedOptimizationSettings.minTableCount,
                AdvancedOptimizationSettings.maxTableCount,
                "桌");
        if ("自动".equals(window) && "自动".equals(table)) {
            return "自动范围";
        }
        return window + " / " + table;
    }

    private String formatOptionalRange(int min, int max, String unit) {
        if (min <= 0 && max <= 0) {
            return "自动";
        }
        if (min <= 0) {
            return "≤" + max + unit;
        }
        if (max <= 0) {
            return "≥" + min + unit;
        }
        return min + "-" + max + unit;
    }

    private String optionalIntText(int value) {
        return value <= 0 ? "" : String.valueOf(value);
    }

    private int parseInt(JTextField field, String label, int min, int max) {
        try {
            int value = Integer.parseInt(field.getText().trim());
            if (value < min || value > max) {
                throw new IllegalArgumentException(label + "必须在 " + min + " 到 " + max + " 之间。");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + "必须是整数。");
        }
    }

    private int parseOptionalInt(JTextField field, String label, int min, int max) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(text);
            if (value < min || value > max) {
                throw new IllegalArgumentException(label + "必须在 " + min + " 到 " + max + " 之间，或留空。");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + "必须是整数，或留空。");
        }
    }

    private long parseLong(JTextField field, String label) {
        try {
            return Long.parseLong(field.getText().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + "必须是整数。");
        }
    }

    private double parseDouble(JTextField field, String label, double min, double max) {
        try {
            double value = Double.parseDouble(field.getText().trim());
            if (value < min || value > max) {
                throw new IllegalArgumentException(label + "必须在 " + min + " 到 " + max + " 之间。");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + "必须是数字。");
        }
    }

    private String mealCode() {
        switch (mealBox.getSelectedIndex()) {
            case 0:
                return "breakfast";
            case 2:
                return "dinner";
            default:
                return "lunch";
        }
    }

    private int mealIndex(String code) {
        if ("breakfast".equals(code)) {
            return 0;
        }
        if ("dinner".equals(code)) {
            return 2;
        }
        return 1;
    }

    private SimulationConfigDTO copy(SimulationConfigDTO source) {
        SimulationConfigDTO dto = new SimulationConfigDTO();
        dto.totalTables = source.totalTables;
        dto.openDuration = source.openDuration;
        dto.totalStudents = source.totalStudents;
        dto.windowCount = source.windowCount;
        dto.probSolo = source.probSolo;
        dto.randomSeed = source.randomSeed;
        dto.avgMealPrice = source.avgMealPrice;
        dto.windowCostPerHour = source.windowCostPerHour;
        dto.tableCost = source.tableCost;
        dto.lostStudentPenalty = source.lostStudentPenalty;
        dto.breakfastPopulationRatio = source.breakfastPopulationRatio;
        dto.lunchPopulationRatio = source.lunchPopulationRatio;
        dto.dinnerPopulationRatio = source.dinnerPopulationRatio;
        dto.simulationMode = source.simulationMode;
        dto.mealPeriod = source.mealPeriod;
        dto.lockedFromOptimization = source.lockedFromOptimization;
        dto.lockedWindowDistances = source.lockedWindowDistances == null ? null : source.lockedWindowDistances.clone();
        dto.lockedWindowAvgServeTime = source.lockedWindowAvgServeTime == null ? null : source.lockedWindowAvgServeTime.clone();
        dto.minWindowCount = source.minWindowCount;
        dto.maxWindowCount = source.maxWindowCount;
        dto.minTableCount = source.minTableCount;
        dto.maxTableCount = source.maxTableCount;
        return dto;
    }

    private static class WidthTrackingPanel extends JPanel implements Scrollable {
        private WidthTrackingPanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 72;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
