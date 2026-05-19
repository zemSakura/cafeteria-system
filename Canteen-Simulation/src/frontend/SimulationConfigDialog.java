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

    // UI 组件：输入框
    private JTextField tablesField;
    private JTextField durationField;
    private JTextField windowCountField;
    private JTextField probSoloField;
    private JTextField seedField;
    private JTextField studentsField;

    // 状态与数据
    private boolean isConfirmed = false;
    private SimulationConfigDTO finalConfig = null;

    public SimulationConfigDialog(Frame parent) {
        // 设置为模态弹窗（不关掉它，就不能点后面的主界面）
        super(parent, "仿真参数初始化配置", true);
        setSize(400, 400);
        setLocationRelativeTo(parent); // 居中显示
        setLayout(new BorderLayout(10, 10));

        // 1. 初始化输入框并填入默认值
        SimulationConfigDTO defaultDto = new SimulationConfigDTO();
        tablesField = new JTextField(String.valueOf(defaultDto.totalTables));
        durationField = new JTextField(String.valueOf(defaultDto.openDuration));
        windowCountField = new JTextField(String.valueOf(defaultDto.windowCount));
        probSoloField = new JTextField(String.valueOf(defaultDto.probSolo));
        seedField = new JTextField(String.valueOf(defaultDto.randomSeed));

        // 2. 组装表单面板 (使用 GridLayout 两列排布)
        JPanel formPanel = new JPanel(new GridLayout(8, 2, 10, 15));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        formPanel.add(new JLabel("就餐区桌子总数 (1-200):"));
        formPanel.add(tablesField);

        formPanel.add(new JLabel("营业总时长 (分钟):"));
        formPanel.add(durationField);

        formPanel.add(new javax.swing.JLabel("食堂就餐总人数:"));
        studentsField = new javax.swing.JTextField("1000"); // 默认给个1000人
        formPanel.add(studentsField);

        formPanel.add(new JLabel("开放窗口数量:"));
        formPanel.add(windowCountField);

        formPanel.add(new JLabel("单人就餐概率 (0.0-1.0):"));
        formPanel.add(probSoloField);

        formPanel.add(new JLabel("随机种子 (用于复现):"));
        formPanel.add(seedField);

        // 【新增】：模拟模式下拉框
        formPanel.add(new JLabel("模拟模式:"));
        String[] modes = {"单时段 (Single Period)", "全天仿真 (Full Day)"};
        modeComboBox = new JComboBox<>(modes);
        formPanel.add(modeComboBox);

        // 【新增】：餐段选择下拉框
        formPanel.add(new JLabel("目标餐段:"));
        String[] meals = {"早餐 (Breakfast)", "午餐 (Lunch)", "晚餐 (Dinner)"};
        mealComboBox = new JComboBox<>(meals);
        // 默认选午餐，因为午餐逻辑最复杂
        mealComboBox.setSelectedIndex(1);
        formPanel.add(mealComboBox);

        // 【高阶联动体验】：如果选了全天，就把餐段下拉框置灰（因为全天模式不需要选单餐）
        modeComboBox.addActionListener(e -> {
            boolean isSingle = modeComboBox.getSelectedIndex() == 0;
            mealComboBox.setEnabled(isSingle);
        });

        this.add(formPanel, BorderLayout.CENTER);

        // 3. 组装底部按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton importBtn = new JButton("导入 JSON 配置");
        JButton cancelBtn = new JButton("取消");
        JButton confirmBtn = new JButton("确认并启动");

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
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON Configuration", "json"));

            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                try {
                    // 读取文件内容
                    String content = new String(Files.readAllBytes(selectedFile.toPath()));

                    // 手动解析（如果没有引入库，可以用正则提取数字）
                    tablesField.setText(extractJsonValue(content, "totalTables"));
                    durationField.setText(extractJsonValue(content, "openDuration"));
                    windowCountField.setText(extractJsonValue(content, "windowCount"));
                    probSoloField.setText(extractJsonValue(content, "probSolo"));
                    seedField.setText(extractJsonValue(content, "randomSeed"));

                    JOptionPane.showMessageDialog(this, "配置已从文件自动填充！");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "文件解析失败，请检查格式！", "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
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

    // --- 核心校验逻辑 ---
    private void handleConfirm() {
        try {
            SimulationConfigDTO dto = new SimulationConfigDTO();
            dto.totalTables = Integer.parseInt(tablesField.getText().trim());
            dto.openDuration = Integer.parseInt(durationField.getText().trim());
            dto.windowCount = Integer.parseInt(windowCountField.getText().trim());
            dto.probSolo = Double.parseDouble(probSoloField.getText().trim());
            dto.randomSeed = Long.parseLong(seedField.getText().trim());
            dto.totalStudents = Integer.parseInt(studentsField.getText().trim());
            dto.simulationMode = modeComboBox.getSelectedIndex() == 0 ? "singlePeriod" : "fullDay";

            int mealIdx = mealComboBox.getSelectedIndex();
            if (mealIdx == 0) dto.mealPeriod = "breakfast";
            else if (mealIdx == 1) dto.mealPeriod = "lunch";
            else dto.mealPeriod = "dinner";

            // 业务规则校验
            if (dto.totalTables <= 0 || dto.totalTables > 200) {
                throw new IllegalArgumentException("桌子数量必须在 1 到 200 之间！");
            }
            if (dto.probSolo < 0.0 || dto.probSolo > 1.0) {
                throw new IllegalArgumentException("概率必须在 0 到 1 之间！");
            }
            if (dto.windowCount <= 0) {
                throw new IllegalArgumentException("窗口数量必须大于 0！");
            }
            if (dto.totalStudents <= 0 || dto.totalStudents > 10000) {
                throw new IllegalArgumentException("总人数应在 1 到 10000 之间！");
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

    // 提供给外部调用的方法：判断用户是点了确认还是取消
    public boolean isConfirmed() {
        return isConfirmed;
    }

    // 提供给外部调用的方法：获取打包好的数据
    public SimulationConfigDTO getConfigData() {
        return finalConfig;
    }
}