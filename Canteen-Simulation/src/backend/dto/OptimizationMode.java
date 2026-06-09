package backend.dto;

/**
 * Controls how the optimizer balances operating profit and service quality.
 */
public enum OptimizationMode {
    PROFIT_FIRST("收益优先"),
    COMPLETION_FIRST("完成率优先"),
    EXPERIENCE_FIRST("体验优先"),
    BALANCED("均衡模式");

    private final String displayName;

    OptimizationMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static OptimizationMode fromDisplayName(String value) {
        for (OptimizationMode mode : values()) {
            if (mode.displayName.equals(value) || mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return BALANCED;
    }
}
