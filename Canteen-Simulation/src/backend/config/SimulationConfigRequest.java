package backend.config;

/**
 * Frontend-to-backend configuration request.
 *
 * Existing UI fields remain supported. New arrival fields allow the frontend to
 * pass totalPopulation, simulationMode and mealPeriod without touching the
 * arrival module directly.
 */
public class SimulationConfigRequest {

    private int tableCount;
    private int windowCount;
    private int openDuration;
    private int mealGapTicks;
    private int snapshotInterval;
    private long randomSeed;

    private double diningTimeMean;
    private double diningTimeStd;
    private int minDiningTime;

    private int patienceMin;
    private int patienceMax;

    private double probSolo;
    private double probDuo;
    private double probTrio;
    private double probTeam;

    private int totalPopulation;
    private String simulationMode;
    private String mealPeriod;

    private int[] windowDistances;
    private int[] windowAvgServeTime;

    public SimulationConfigRequest() {
        this.tableCount = CanteenConfig.DEFAULT_TOTAL_TABLES;
        this.windowCount = CanteenConfig.DEFAULT_WINDOW_DISTANCES.length;
        this.openDuration = CanteenConfig.DEFAULT_OPEN_DURATION;
        this.mealGapTicks = CanteenConfig.DEFAULT_MEAL_GAP_TICKS;
        this.snapshotInterval = CanteenConfig.DEFAULT_SNAPSHOT_INTERVAL;
        this.randomSeed = CanteenConfig.DEFAULT_RANDOM_SEED;

        this.diningTimeMean = CanteenConfig.DEFAULT_DINING_TIME_MEAN;
        this.diningTimeStd = CanteenConfig.DEFAULT_DINING_TIME_STD;
        this.minDiningTime = CanteenConfig.DEFAULT_MIN_DINING_TIME;

        this.patienceMin = CanteenConfig.DEFAULT_PATIENCE_MIN;
        this.patienceMax = CanteenConfig.DEFAULT_PATIENCE_MAX;

        this.probSolo = CanteenConfig.DEFAULT_PROB_SOLO;
        this.probDuo = CanteenConfig.DEFAULT_PROB_DUO;
        this.probTrio = CanteenConfig.DEFAULT_PROB_TRIO;
        this.probTeam = CanteenConfig.DEFAULT_PROB_TEAM;

        this.totalPopulation = CanteenConfig.DEFAULT_TOTAL_POPULATION;
        this.simulationMode = CanteenConfig.DEFAULT_SIMULATION_MODE.getCode();
        this.mealPeriod = CanteenConfig.DEFAULT_MEAL_PERIOD.getCode();
    }

    public int getTableCount() {
        return tableCount;
    }

    public void setTableCount(int tableCount) {
        this.tableCount = tableCount;
    }

    public int getWindowCount() {
        return windowCount;
    }

    public void setWindowCount(int windowCount) {
        this.windowCount = windowCount;
    }

    public int getOpenDuration() {
        return openDuration;
    }

    public void setOpenDuration(int openDuration) {
        this.openDuration = openDuration;
    }

    public int getMealGapTicks() {
        return mealGapTicks;
    }

    public void setMealGapTicks(int mealGapTicks) {
        this.mealGapTicks = mealGapTicks;
    }

    public int getSnapshotInterval() {
        return snapshotInterval;
    }

    public void setSnapshotInterval(int snapshotInterval) {
        this.snapshotInterval = snapshotInterval;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public void setRandomSeed(long randomSeed) {
        this.randomSeed = randomSeed;
    }

    public double getDiningTimeMean() {
        return diningTimeMean;
    }

    public void setDiningTimeMean(double diningTimeMean) {
        this.diningTimeMean = diningTimeMean;
    }

    public double getDiningTimeStd() {
        return diningTimeStd;
    }

    public void setDiningTimeStd(double diningTimeStd) {
        this.diningTimeStd = diningTimeStd;
    }

    public int getMinDiningTime() {
        return minDiningTime;
    }

    public void setMinDiningTime(int minDiningTime) {
        this.minDiningTime = minDiningTime;
    }

    public int getPatienceMin() {
        return patienceMin;
    }

    public void setPatienceMin(int patienceMin) {
        this.patienceMin = patienceMin;
    }

    public int getPatienceMax() {
        return patienceMax;
    }

    public void setPatienceMax(int patienceMax) {
        this.patienceMax = patienceMax;
    }

    public double getProbSolo() {
        return probSolo;
    }

    public void setProbSolo(double probSolo) {
        this.probSolo = probSolo;
        distributeRemainingGroupProbabilities();
    }

    public double getProbDuo() {
        return probDuo;
    }

    public void setProbDuo(double probDuo) {
        this.probDuo = probDuo;
    }

    public double getProbTrio() {
        return probTrio;
    }

    public void setProbTrio(double probTrio) {
        this.probTrio = probTrio;
    }

    public double getProbTeam() {
        return probTeam;
    }

    public void setProbTeam(double probTeam) {
        this.probTeam = probTeam;
    }

    public int getTotalPopulation() {
        return totalPopulation;
    }

    public void setTotalPopulation(int totalPopulation) {
        this.totalPopulation = totalPopulation;
    }

    public String getSimulationMode() {
        return simulationMode;
    }

    public void setSimulationMode(String simulationMode) {
        this.simulationMode = simulationMode;
    }

    public String getMealPeriod() {
        return mealPeriod;
    }

    public void setMealPeriod(String mealPeriod) {
        this.mealPeriod = mealPeriod;
    }

    public int[] getWindowDistances() {
        return windowDistances;
    }

    public void setWindowDistances(int[] windowDistances) {
        this.windowDistances = windowDistances;
    }

    public int[] getWindowAvgServeTime() {
        return windowAvgServeTime;
    }

    public void setWindowAvgServeTime(int[] windowAvgServeTime) {
        this.windowAvgServeTime = windowAvgServeTime;
    }

    /**
     * The UI exposes only the solo probability. Keep the companion group-size
     * probabilities consistent even if callers set only probSolo.
     */
    private void distributeRemainingGroupProbabilities() {
        double boundedSolo = Math.max(0.0, Math.min(1.0, this.probSolo));
        this.probSolo = boundedSolo;

        double remainder = 1.0 - boundedSolo;
        this.probDuo = remainder * 0.5;
        this.probTrio = remainder * 0.3;
        this.probTeam = 1.0 - boundedSolo - this.probDuo - this.probTrio;
    }
}
