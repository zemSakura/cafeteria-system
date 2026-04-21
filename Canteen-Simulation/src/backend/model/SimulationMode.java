package backend.model;

/**
 * Arrival simulation scope.
 */
public enum SimulationMode {
    SINGLE_PERIOD("singlePeriod"),
    FULL_DAY("fullDay");

    private final String code;

    SimulationMode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static SimulationMode fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return SINGLE_PERIOD;
        }

        String normalized = code.trim().replace("_", "").replace("-", "").toLowerCase();
        if ("fullday".equals(normalized) || "day".equals(normalized) || "all".equals(normalized)) {
            return FULL_DAY;
        }
        if ("singleperiod".equals(normalized) || "single".equals(normalized) || "period".equals(normalized)) {
            return SINGLE_PERIOD;
        }

        throw new IllegalArgumentException("Unsupported simulationMode: " + code);
    }
}
