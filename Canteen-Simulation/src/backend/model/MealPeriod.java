package backend.model;

/**
 * Meal period supported by the arrival initialization module.
 *
 * Time is stored as minute-of-day so backend data can represent a whole day
 * while the simulation engine can still consume monotonically increasing times.
 */
public enum MealPeriod {
    BREAKFAST("breakfast", "Breakfast", 6 * 60 + 30, 9 * 60),
    LUNCH("lunch", "Lunch", 11 * 60, 13 * 60 + 30),
    DINNER("dinner", "Dinner", 17 * 60, 19 * 60 + 30);

    private final String code;
    private final String displayName;
    private final int startMinute;
    private final int endMinute;

    MealPeriod(String code, String displayName, int startMinute, int endMinute) {
        this.code = code;
        this.displayName = displayName;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public int getEndMinute() {
        return endMinute;
    }

    public int getDurationMinutes() {
        return endMinute - startMinute;
    }

    public static MealPeriod fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return LUNCH;
        }

        String normalized = code.trim().toLowerCase();
        for (MealPeriod period : values()) {
            if (period.code.equals(normalized) || period.name().equalsIgnoreCase(normalized)) {
                return period;
            }
        }

        throw new IllegalArgumentException("Unsupported mealPeriod: " + code);
    }
}
