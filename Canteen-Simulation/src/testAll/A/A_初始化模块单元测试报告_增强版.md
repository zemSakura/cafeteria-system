# A：初始化模块（人员到来模块）单元测试报告

---

## 一、测试对象

本单元测试针对食堂仿真系统中 **A 模块（初始化模块 / 人员到来模块）** 的以下 12 个 Java 源文件进行测试：

| 序号 | 文件名 | 所属包 | 职责说明 |
|------|--------|--------|----------|
| 1 | `CanteenConfig.java` | `backend.config` | 全局静态配置类，管理仿真所有运行参数及默认值 |
| 2 | `SimulationConfigRequest.java` | `backend.config` | 前后端配置传输对象（DTO），封装前端表单数据 |
| 3 | `CanteenSimulationEngine.java` | `backend.engine` | 仿真总引擎，负责配置应用、启动、停止、重置 |
| 4 | `ArrivalModule.java` | `backend.module` | 到达初始化核心模块，负责人员生成与到达计划 |
| 5 | `ArrivalDistributionPoint.java` | `backend.model` | 分钟级到达强度数据点，用于前端可视化 |
| 6 | `ArrivalGenerationResult.java` | `backend.model` | 到达生成结果封装，包含学生列表、事件、统计 |
| 7 | `ArrivalPeak.java` | `backend.model` | 高斯峰值描述模型，定义到达高峰的参数 |
| 8 | `CrowdType.java` | `backend.model` | 人群类型枚举（FAST / NORMAL / SLOW） |
| 9 | `Group.java` | `backend.model` | 到达小组模型，封装同组学生 |
| 10 | `MealArrivalStats.java` | `backend.model` | 每餐段统计信息，包含人口分布与峰值数据 |
| 11 | `MealPeriod.java` | `backend.model` | 餐段枚举（BREAKFAST / LUNCH / DINNER），含时间范围定义 |
| 12 | `SimulationMode.java` | `backend.model` | 仿真模式枚举（SINGLE_PERIOD / FULL_DAY） |

---

## 二、测试目的

本单元测试旨在验证初始化模块的功能正确性与鲁棒性，具体包括以下几个方面：

1. **验证人员生成逻辑正确性**：确保 `deriveMealPopulations` 方法在各种总人数输入下能正确分配早餐、午餐、晚餐的用餐人数，且满足"早餐人数 < 晚餐人数 < 午餐人数"的校园就餐层级规律。
2. **验证到达时间分布合理性**：确保生成的到达时间落在对应餐段的营业时间范围内，FULL_DAY 模式下三餐段时间连续且不重叠，学生列表按到达时间有序排列。
3. **验证人员对象生成完整性**：确保每个学生对象的必要字段（id、groupId、arrivalTime、mealPeriod、crowdType 等）均被正确赋值且无重复。
4. **验证边界与异常输入处理能力**：确保模块在接收到非法输入（负数、零、null、极大值、非法配置值）时能正确抛出异常或安全退化。

---

## 三、测试内容

本测试从以下四个维度对 A 模块进行全面覆盖：

### 1. 函数输入输出正确性

验证各公开方法的输入与输出之间的对应关系是否正确，包括：
- `deriveMealPopulations(int totalPopulation)` 返回的 Map 中各餐段人数是否合理
- `generateArrivalPlan()` 返回的学生数量是否与分配人数一致
- `initWindows()` 返回的窗口数量是否与配置一致

### 2. 模块逻辑正确性

验证模块内部业务逻辑是否符合设计预期，包括：
- 早餐人数 < 晚餐人数 < 午餐人数 的层级规则是否始终成立
- FULL_DAY 模式下三餐段时间是否连续不重叠
- 学生 ID 是否全局唯一且自增
- 同组学生是否具有相同的 groupId 和 arrivalTime
- 到达事件是否按时间升序排列

### 3. 边界条件处理

验证模块在边界输入下的行为是否正确，包括：
- 最小有效值 totalPopulation=1
- 小规模值 totalPopulation=10
- null SimulationMode / null MealPeriod 的默认行为

### 4. 异常输入处理

验证模块对非法输入的防御能力，包括：
- 负数 totalPopulation（-1）
- 零 totalPopulation（0）
- 极大值 totalPopulation（Integer.MAX_VALUE）
- null SimulationConfigRequest
- 非法配置值（概率值超出 [0,1]、负数营业时长、窗口数组长度不匹配）

---

## 四、测试方法

### 测试框架

采用纯 Java 实现的手写单元测试框架，每个测试类均包含独立的 `main` 方法，可单独编译运行，无需依赖 JUnit 等第三方测试库。

每个测试类的结构如下：类内部维护 `testPassCount` 和 `testFailCount` 两个静态计数器，每个测试方法独立执行并累加 PASS/FAIL 计数，最终由 `printSummary()` 方法输出汇总结果。以下为测试类的典型结构：

```java
public class InitPopulationTest {

    private static int testPassCount = 0;
    private static int testFailCount = 0;

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("  InitPopulationTest - 人口初始化分配测试");
        System.out.println("==============================================");
        runAll();
        printSummary();
    }

    public static int runAll() {
        testPassCount = 0;
        testFailCount = 0;
        CanteenConfig.resetToDefaults();
        // 依次调用各测试方法...
        return testFailCount;
    }
}
```

### 测试策略

| 测试策略 | 说明 |
|----------|------|
| 白盒测试 | 基于源代码逻辑设计测试用例，验证内部方法行为 |
| 黑盒测试 | 将模块视为黑盒，通过公开接口验证输入输出关系 |
| 确定性测试 | 使用固定随机种子（randomSeed=20260324L）保证测试结果可复现 |

### 输入数据设计

测试数据采用模拟数据（simulated data），覆盖四个类别：

| 数据类别 | 具体取值 | 设计目的 |
|----------|----------|----------|
| 正常数据 | 100、1000、3000 | 模拟小、中、大型学校/园区的典型就餐人数 |
| 边界数据 | 0、1、10 | 验证极端小规模场景下的边界行为 |
| 大规模数据 | 20000、50000 | 验证算法在大数据量下的性能和正确性 |
| 非法/异常数据 | -1、Integer.MAX_VALUE、null | 验证错误输入时的异常处理机制 |

### PASS / FAIL 判定方式

每个测试用例均遵循统一的判定规则：
1. 预先定义输入数据和预期结果
2. 执行被测方法，获取实际结果
3. 将实际结果与预期结果比较
4. 一致则输出 `[PASS]`，不一致则输出 `[FAIL]`

以下为 PASS/FAIL 判定的典型实现模式。该代码展示了测试方法的标准结构——先打印输入数据和预期结果，再调用被测方法获取实际结果，最后进行逐一验证判断：

```java
private static void testDeriveMealPopulations_Normal(
        String testName, int totalPopulation) {

    System.out.println("[" + testName + "]");
    System.out.println("输入数据：总人数 = " + totalPopulation);

    ArrivalModule module = new ArrivalModule(20260324L);
    Map<MealPeriod, Integer> result =
            module.deriveMealPopulations(totalPopulation);

    int breakfast = result.get(MealPeriod.BREAKFAST);
    int lunch = result.get(MealPeriod.LUNCH);
    int dinner = result.get(MealPeriod.DINNER);

    System.out.println("预期结果：各餐段人数 > 0 且 <= " + totalPopulation);
    System.out.println("实际结果：早餐=" + breakfast
            + ", 午餐=" + lunch + ", 晚餐=" + dinner);

    // 逐一验证
    boolean allPositive = breakfast > 0 && lunch > 0 && dinner > 0;
    if (allPositive) {
        System.out.println("[PASS] 各餐段人数均 > 0");
        testPassCount++;
    } else {
        System.out.println("[FAIL] 存在餐段人数 <= 0");
        testFailCount++;
    }
}
```

该判定模式具有以下特点：每个验证点独立计数，一个测试方法可包含多个 PASS/FAIL 判定，最终在汇总阶段统计全部通过项和失败项。这种细粒度的判定方式便于精确定位问题所在。

---

## 五、测试用例设计

### 5.1 正常测试用例

| 测试编号 | 测试内容 | 输入数据 | 预期结果 | 实际结果 | 是否通过 |
| -------- | ---- | ---- | ---- | ---- | ---- |
| TC-A-001 | 人口分配-总人数100 | totalPopulation=100 | 各餐段人数 > 0 且 <= 100 | 早餐=21, 午餐=63, 晚餐=45 | PASS |
| TC-A-002 | 人口分配-总人数1000 | totalPopulation=1000 | 各餐段人数 > 0 且 <= 1000 | 早餐=206, 午餐=634, 晚餐=447 | PASS |
| TC-A-003 | 人口分配-总人数3000 | totalPopulation=3000 | 各餐段人数 > 0 且 <= 3000 | 早餐=618, 午餐=1901, 晚餐=1341 | PASS |
| TC-A-004 | 早餐到达时间-100人 | totalPopulation=100, period=BREAKFAST | 所有时间为 06:30-09:00 内，不越界 | 生成21人，越界0人 | PASS |
| TC-A-005 | 午餐到达时间-1000人 | totalPopulation=1000, period=LUNCH | 所有时间为 11:00-13:30 内，不越界 | 生成634人，越界0人 | PASS |
| TC-A-006 | 晚餐到达时间-3000人 | totalPopulation=3000, period=DINNER | 所有时间为 17:00-19:30 内，不越界 | 生成1341人，越界0人 | PASS |
| TC-A-007 | 学生字段完整性-午餐1000人 | totalPopulation=1000, period=LUNCH | id>0, groupId>0, arrivalTime>=0 | 无效字段数均为0 | PASS |
| TC-A-008 | 学生ID唯一性-午餐1000人 | totalPopulation=1000, period=LUNCH | 所有ID唯一无重复 | 唯一ID数=634, 重复数=0 | PASS |
| TC-A-009 | 学生ID唯一性-全天线3000人 | totalPopulation=3000, mode=FULL_DAY | 所有ID唯一无重复 | 唯一ID数=3860, 重复数=0 | PASS |
| TC-A-010 | 到达事件生成-午餐1000人 | totalPopulation=1000, period=LUNCH | 事件数=学生数，时间有序 | 学生634, 事件634, 有序 | PASS |
| TC-A-011 | 窗口初始化-默认配置 | 默认窗口数=5 | 生成5个窗口和5个窗口状态 | initWindows=5, initWindowStates=5 | PASS |
| TC-A-012 | 引擎启动-默认配置 | 默认CanteenConfig | 引擎启动后生成学生、窗口、桌位 | 学生>0, 窗口=5, 桌位=150 | PASS |
| TC-A-013 | 引擎启动-自定义配置 | totalPopulation=500, tableCount=100 | 桌位按配置为100，学生>0 | 桌位=100, 学生=317 | PASS |
| TC-A-014 | generateStudents一致性 | totalPopulation=1000, period=LUNCH | 两种方式生成学生数一致 | 均为634人 | PASS |
| TC-A-015 | null SimulationMode默认值 | totalPopulation=100, mode=null | 默认为SINGLE_PERIOD | 模式=SINGLE_PERIOD | PASS |
| TC-A-016 | null MealPeriod默认值 | totalPopulation=100, period=null | 默认为LUNCH | 餐段=Lunch | PASS |
| TC-A-017 | 配置恢复-resetToDefaults | 修改TOTAL_POPULATION=5000后重置 | 恢复为默认值1000 | TOTAL_POPULATION=1000 | PASS |

以下为正常测试用例中"层级关系验证"的典型测试代码。该测试验证早餐、午餐、晚餐人数是否满足 breakfast < dinner < lunch 的校园就餐规律，是上述 TC-A-001 至 TC-A-003 背后的人口分配逻辑正确性测试：

```java
private static void testHierarchyRule(
        String testName, int totalPopulation) {

    System.out.println("[" + testName + "]");
    System.out.println("输入数据：总人数 = " + totalPopulation);

    ArrivalModule module = new ArrivalModule(20260324L);
    Map<MealPeriod, Integer> result =
            module.deriveMealPopulations(totalPopulation);

    int breakfast = result.get(MealPeriod.BREAKFAST);
    int lunch = result.get(MealPeriod.LUNCH);
    int dinner = result.get(MealPeriod.DINNER);

    System.out.println("预期结果：早餐(" + breakfast
            + ") < 晚餐(" + dinner + ") < 午餐(" + lunch + ")");
    System.out.println("实际结果：早餐=" + breakfast
            + ", 午餐=" + lunch + ", 晚餐=" + dinner);

    if (breakfast < dinner && dinner < lunch) {
        System.out.println("[PASS] 满足 breakfast(" + breakfast
                + ") < dinner(" + dinner + ") < lunch(" + lunch + ")");
        testPassCount++;
    } else {
        System.out.println("[FAIL] 不满足层级关系");
        testFailCount++;
    }
}
```

以下为正常测试用例中"到达时间范围验证"的典型代码。该测试遍历生成的每个学生对象，逐一检查其到达时间是否在对应餐段的时间范围内，对应上表中的 TC-A-004 至 TC-A-006：

```java
private static void testArrivalTimeInRange(
        String testName, int totalPopulation, MealPeriod period) {

    System.out.println("[" + testName + "]");
    System.out.println("输入数据：总人数=" + totalPopulation
            + ", 餐段=" + period.getCode());

    ArrivalModule module = new ArrivalModule(20260324L);
    ArrivalGenerationResult result = module.generateArrivalPlan(
            totalPopulation, SimulationMode.SINGLE_PERIOD, period);
    List<Student> students = result.getStudents();

    int startMinute = period.getStartMinute();
    int endMinute = period.getEndMinute();

    int outOfRangeCount = 0;
    for (Student s : students) {
        long time = s.getArrivalTime();
        if (time < 0 || time > (endMinute - startMinute)) {
            outOfRangeCount++;
        }
    }

    System.out.println("实际结果：学生总数=" + students.size()
            + ", 越界人数=" + outOfRangeCount);

    if (outOfRangeCount == 0) {
        System.out.println("[PASS] 所有到达时间均在时段范围内");
        testPassCount++;
    } else {
        System.out.println("[FAIL] 有 " + outOfRangeCount
                + " 人的到达时间超出范围");
        testFailCount++;
    }
}
```

### 5.2 边界测试用例

| 测试编号 | 测试内容 | 输入数据 | 预期结果 | 实际结果 | 是否通过 |
| -------- | ---- | ---- | ---- | ---- | ---- |
| TC-A-B01 | 最小有效值 | totalPopulation=1 | 正常生成，学生数 > 0 | 生成1人 | PASS |
| TC-A-B02 | 小规模值 | totalPopulation=10 | 正常生成，学生数 > 0 | 生成6人 | PASS |
| TC-A-B03 | 早餐到达时间-1人 | totalPopulation=1, period=BREAKFAST | 1人时间在范围内 | 到达时间在06:30-09:00内 | PASS |
| TC-A-B04 | 午餐到达时间-10人 | totalPopulation=10, period=LUNCH | 10人时间在范围内 | 6人在11:00-13:30内 | PASS |
| TC-A-B05 | ArrivalModule无参构造函数 | new ArrivalModule() | 正常创建，seed=默认值 | 创建成功 | PASS |
| TC-A-B06 | ArrivalModule带种子构造函数 | new ArrivalModule(12345L) | seed=12345 | seed=12345 | PASS |
| TC-A-B07 | resetForNextRun确定性 | 相同种子两次生成 | 两次结果一致 | 人数一致 | PASS |

以下为边界测试中"确定性随机种子验证"的测试代码。该测试使用同一个随机种子两次调用 generateArrivalPlan，验证中间经过 resetForNextRun 重置后两次生成结果一致，对应上表中的 TC-A-B07：

```java
private static void testResetForNextRun(String testName) {

    System.out.println("[" + testName + "]");
    System.out.println("输入数据：使用相同种子两次生成，"
            + "中间调用 resetForNextRun");

    ArrivalModule module = new ArrivalModule(20260324L);
    var result1 = module.generateArrivalPlan(
            100, SimulationMode.SINGLE_PERIOD, MealPeriod.LUNCH);
    int count1 = result1.getStudents().size();

    module.resetForNextRun(20260324L);
    var result2 = module.generateArrivalPlan(
            100, SimulationMode.SINGLE_PERIOD, MealPeriod.LUNCH);
    int count2 = result2.getStudents().size();

    System.out.println("实际结果：第一次人数=" + count1
            + ", 重置后第二次人数=" + count2);

    if (count1 == count2) {
        System.out.println("[PASS] resetForNextRun 后"
                + "使用相同种子生成结果一致");
        testPassCount++;
    } else {
        System.out.println("[FAIL] 两次生成人数不一致: "
                + count1 + " vs " + count2);
        testFailCount++;
    }
}
```

### 5.3 大规模测试用例

| 测试编号 | 测试内容 | 输入数据 | 预期结果 | 实际结果 | 是否通过 |
| -------- | ---- | ---- | ---- | ---- | ---- |
| TC-A-L01 | 人口分配-20000人 | totalPopulation=20000 | 各餐段人数 > 0, 比例合理 | 早餐=4120, 午餐=12672, 晚餐=8938 | PASS |
| TC-A-L02 | 人口分配-50000人 | totalPopulation=50000 | 各餐段人数 > 0, 比例合理 | 早餐=10300, 午餐=31680, 晚餐=22345 | PASS |
| TC-A-L03 | 午餐到达时间-50000人 | totalPopulation=50000, period=LUNCH | 大规模下时间不越界 | 生成31680人，越界0人 | PASS |
| TC-A-L04 | 晚餐到达时间-20000人 | totalPopulation=20000, period=DINNER | 大规模下时间不越界 | 生成8938人，越界0人 | PASS |
| TC-A-L05 | FULL_DAY-20000人 | totalPopulation=20000, mode=FULL_DAY | 三餐均有学生，时间连续 | 早餐4120, 午餐12672, 晚餐8938 | PASS |
| TC-A-L06 | 人群类型分布-20000人 | totalPopulation=20000 | 三种类型均有覆盖 | FAST/NORMAL/SLOW均有 | PASS |

以下为大规模测试中"FULL_DAY 模式三餐连续性验证"的测试代码。该测试在全天线模式下生成三个餐段的学生，通过比较各餐段的最晚和最早到达时间来判断时间是否连续无重叠，对应上表中的 TC-A-L05：

```java
private static void testFullDayTimeContinuity(
        String testName, int totalPopulation) {

    System.out.println("[" + testName + "]");
    System.out.println("输入数据：总人数=" + totalPopulation
            + ", 模式=FULL_DAY");

    ArrivalModule module = new ArrivalModule(20260324L);
    ArrivalGenerationResult result = module.generateArrivalPlan(
            totalPopulation,
            SimulationMode.FULL_DAY,
            MealPeriod.BREAKFAST);
    List<Student> students = result.getStudents();

    // 遍历所有学生，分别找三餐的最早/最晚时间
    long breakfastMax = Long.MIN_VALUE;
    long lunchMin = Long.MAX_VALUE, lunchMax = Long.MIN_VALUE;
    long dinnerMin = Long.MAX_VALUE;

    for (Student s : students) {
        MealPeriod p = s.getMealPeriod();
        long t = s.getArrivalTime();
        if (p == MealPeriod.BREAKFAST && t > breakfastMax)
            breakfastMax = t;
        else if (p == MealPeriod.LUNCH) {
            if (t < lunchMin) lunchMin = t;
            if (t > lunchMax) lunchMax = t;
        } else if (p == MealPeriod.DINNER && t < dinnerMin)
            dinnerMin = t;
    }

    // 验证：早餐最晚 <= 午餐最早，午餐最晚 <= 晚餐最早
    if (breakfastMax <= lunchMin) {
        System.out.println("[PASS] 早餐在午餐之前");
        testPassCount++;
    } else {
        System.out.println("[FAIL] 早餐与午餐时间重叠");
        testFailCount++;
    }

    if (lunchMax <= dinnerMin) {
        System.out.println("[PASS] 午餐在晚餐之前");
        testPassCount++;
    } else {
        System.out.println("[FAIL] 午餐与晚餐时间重叠");
        testFailCount++;
    }
}
```

### 5.4 非法输入测试用例

| 测试编号 | 测试内容 | 输入数据 | 预期结果 | 实际结果 | 是否通过 |
| -------- | ---- | ---- | ---- | ---- | ---- |
| TC-A-E01 | 负数totalPopulation | totalPopulation=-1 | 抛出IllegalArgumentException | 正确抛出异常 | PASS |
| TC-A-E02 | 零totalPopulation | totalPopulation=0 | 抛出IllegalArgumentException | 正确抛出异常 | PASS |
| TC-A-E03 | Integer.MAX_VALUE极端值 | totalPopulation=Integer.MAX_VALUE | 完成计算或抛出可预期异常 | deriveMealPopulations内部double乘法结果溢出，强制clamp导致三值为(1,1,1)，不满足层级关系 | FAIL |
| TC-A-E04 | null SimulationConfigRequest | request=null | 抛出IllegalArgumentException | 正确抛出异常 | PASS |
| TC-A-E05 | 非法窗口数量 | windowCount=0 | 抛出IllegalArgumentException | 正确抛出异常 | PASS |
| TC-A-E06 | null窗口配置数组 | distances=null, serveTimes=null | 抛出IllegalArgumentException | 正确抛出异常 | PASS |
| TC-A-E07 | 窗口数组长度不匹配 | distances长度3, serveTimes长度5 | 抛出IllegalArgumentException | 正确抛出异常 | PASS |
| TC-A-E08 | 非法概率值 | PROB_SOLO=1.5 | 抛出IllegalArgumentException | 正确抛出异常 | PASS |
| TC-A-E09 | 负数营业时长 | OPEN_DURATION=-1 | 抛出IllegalArgumentException | 正确抛出异常 | PASS |

以下为非法输入测试中"异常捕获验证"的典型代码。该测试使用 try-catch 结构验证被测方法在接收非法输入时是否按预期抛出 IllegalArgumentException，对应上表中的 TC-A-E01 和 TC-A-E02：

```java
private static void testNegativePopulation(
        String testName, int population) {

    System.out.println("[" + testName + "]");
    System.out.println("输入数据：totalPopulation = " + population);
    System.out.println("预期结果：抛出 IllegalArgumentException");

    try {
        ArrivalModule module = new ArrivalModule(20260324L);
        module.generateArrivalPlan(
                population,
                SimulationMode.SINGLE_PERIOD,
                MealPeriod.LUNCH);
        System.out.println("实际结果：没有抛出异常");
        System.out.println("[FAIL] 应抛出异常但未抛出");
        testFailCount++;
    } catch (IllegalArgumentException e) {
        System.out.println("实际结果：抛出 IllegalArgumentException: "
                + e.getMessage());
        System.out.println("[PASS] 正确抛出 IllegalArgumentException");
        testPassCount++;
    }
}
```

以下为配置校验的非法输入测试代码。该测试验证 CanteenConfig.validate() 方法是否能检测出概率值超出 [0,1] 范围的非法配置，对应上表中的 TC-A-E08：

```java
private static void testInvalidRate(String testName) {

    System.out.println("[" + testName + "]");
    System.out.println("输入数据：PROB_SOLO = 1.5 (超出范围)");
    System.out.println("预期结果：抛出 IllegalArgumentException");

    CanteenConfig.resetToDefaults();

    try {
        CanteenConfig.PROB_SOLO = 1.5;
        CanteenConfig.validate();
        System.out.println("实际结果：没有抛出异常");
        System.out.println("[FAIL] 非法概率值应抛出异常");
        testFailCount++;
    } catch (IllegalArgumentException e) {
        System.out.println("实际结果：抛出 IllegalArgumentException: "
                + e.getMessage());
        System.out.println("[PASS] 正确抛出 IllegalArgumentException");
        testPassCount++;
    } finally {
        CanteenConfig.resetToDefaults();
    }
}
```

---

## 六、实际测试结果

### 6.1 总体结果

本次单元测试共设计 **40 个测试用例**，涵盖正常测试、边界测试、大规模测试和非法输入测试四个类别。测试执行结果如下：

| 测试类 | 测试用例数 | PASS | FAIL | 通过率 |
|--------|-----------|------|------|--------|
| InitPopulationTest（人口分配测试） | 7 | 7 | 0 | 100% |
| ArrivalTimeTest（到达时间测试） | 8 | 8 | 0 | 100% |
| PersonInitTest（人员生成测试） | 5 | 5 | 0 | 100% |
| BoundaryTest（边界与异常测试） | 7 | 6 | 1 | 85.7% |
| InitModuleIntegrationTest（集成测试） | 13 | 13 | 0 | 100% |
| **合计** | **40** | **39** | **1** | **97.5%** |

### 6.2 详细运行数据

**人口分配结果（固定种子 20260324L）**：

| 总人数 | 早餐人数 | 午餐人数 | 晚餐人数 | 早餐占比 | 午餐占比 | 晚餐占比 |
|--------|----------|----------|----------|----------|----------|----------|
| 100 | 21 | 63 | 45 | 21.00% | 63.00% | 45.00% |
| 1000 | 206 | 634 | 447 | 20.60% | 63.40% | 44.70% |
| 3000 | 618 | 1901 | 1341 | 20.60% | 63.37% | 44.70% |
| 20000 | 4120 | 12672 | 8938 | 20.60% | 63.36% | 44.69% |
| 50000 | 10300 | 31680 | 22345 | 20.60% | 63.36% | 44.69% |

从上表可见，各餐段人数比例在不同总人数规模下保持高度稳定，早餐稳定在约 20.60%，午餐约 63.36%，晚餐约 44.69%，且始终满足"早餐 < 晚餐 < 午餐"的层级关系。

**FULL_DAY 模式三餐时间连续性**：

| 总人数 | 早餐最晚 | 午餐最早 | 午餐最晚 | 晚餐最早 | 是否连续 |
|--------|----------|----------|----------|----------|----------|
| 100 | 114 | 150 | 289 | 317 | 是 |
| 1000 | 144 | 150 | 299 | 300 | 是 |
| 20000 | 150 | 150 | 300 | 300 | 是 |

三餐段时间均无重叠，满足食堂全天候仿真的时间连续要求。

**人群类型覆盖（午餐 1000 人）**：

| 类型 | 人数 | 占比 |
|------|------|------|
| FAST | 161 | 25.39% |
| NORMAL | 348 | 54.89% |
| SLOW | 125 | 19.72% |

三种人群类型均有覆盖，符合预期分布（FAST≈25%、NORMAL≈55%、SLOW≈20%）。

### 6.3 失败用例分析

**TC-A-E03（Integer.MAX_VALUE 极端值测试）— FAIL**

- **失败原因**：当 `totalPopulation = Integer.MAX_VALUE (2,147,483,647)` 作为输入传递给 `deriveMealPopulations()` 时，方法内部的 `Math.round(totalPopulation * rate)` 计算使用 double 类型进行乘法，随后强制转换为 int。由于 Integer.MAX_VALUE 乘以概率系数后的值超出了 int 的表示范围（-2,147,483,648 ~ 2,147,483,647），导致整数溢出，产生了不正确的计算结果。三个餐段的分配结果均为 1，不满足"早餐 < 晚餐 < 午餐"的层级关系。
- **影响评估**：该场景在实际应用中几乎不可能出现（校园人数不可能达到 21 亿），属于理论边界。但暴露了方法内部缺乏对极端大值的溢出防护。
- **建议改进**：在 `deriveMealPopulations()` 方法开头增加对 `totalPopulation` 的上限校验（如 `if (totalPopulation > 10_000_000)`），在超出合理范围时直接抛出有意义的异常信息。

---

## 七、结果分析

### 7.1 通过测试分析

1. **人口分配逻辑正确**：`deriveMealPopulations()` 方法基于 CanteenConfig 中的概率参数（BREAKFAST_BASE_RATE、LUNCH_BASE_RATE、DINNER_BASE_RATE 等）进行计算，结果在所有测试的总人数规模下均满足层级关系。分配比例稳定，说明算法具备良好的可预测性。

2. **到达时间生成合理**：所有生成的到达时间均在对应餐段的时间范围内（使用 `clamp()` 函数确保边界安全），FULL_DAY 模式下三餐段时间严格连续，且学生列表按到达时间有序排列。高斯峰值分布模型通过 `createPeaks()` 和 `addPeakCandidates()` 实现了基于钟形曲线的到达密度控制。

3. **学生对象字段完整**：生成的学生对象中，id 从 1 开始自增无重复，groupId 正确分组且同组一致性良好，三种 CrowdType 按预设概率分布（FAST≈25%、NORMAL≈55%、SLOW≈20%）。分组大小严格控制在 1-4 人（基于 PROB_SOLO=70%、PROB_DUO=20%、PROB_TEAM=10% 的概率分配）。

4. **异常处理机制完善**：对于负数、零、null 等非法输入，模块统一抛出 `IllegalArgumentException` 并提供有意义的错误信息。CanteenConfig.validate() 方法包含全面的配置校验逻辑（数组非空、长度匹配、概率范围、正数约束等），确保在配置错误时快速失败。

5. **引擎集成正常**：CanteenSimulationEngine 能够正确地应用配置、启动引擎、初始化窗口和桌位、生成到达计划，并通过 `stopEngine()` 和 `resetEngine()` 完成状态清理。自定义配置能够正确覆盖默认值。

### 7.2 模块稳定性评价

通过 97.5% 的测试通过率（仅 1 例失败且为极端理论边界），可以认定：

- 模块在正常业务场景下运行稳定可靠
- 边界条件处理得当，最小有效值（totalPopulation=1）和空输入（null）均有妥善处理
- 大规模数据（50000 人）下性能表现正常，未出现性能劣化
- 存在的 1 个失败用例属于理论极端场景，对实际应用无影响

---

## 八、测试结论

### 8.1 模块整体评价

A 模块（初始化模块 / 人员到来模块）基本满足设计要求：

1. **功能完整性**：能够基于总人数正确分配各餐段用餐人数，并生成带有合理到达时间、人群类型和分组信息的学生列表。FULL_DAY 和 SINGLE_PERIOD 两种仿真模式均可正常工作。

2. **正确性**：人口分配结果满足校园就餐的层级规律（早餐 < 晚餐 < 午餐），比例分配稳定可复现；到达时间严格约束在餐段时间范围内；学生对象字段完整且唯一。

3. **鲁棒性**：对常见非法输入（负数、零、null）具备完善的防御性检查，异常信息清晰明确。配置校验覆盖全面（概率值、数组长度、参数范围等）。

4. **可维护性**：模块结构清晰，公开接口与私有方法职责分明，便于后续扩展和单独测试。

### 8.2 是否具备实际运行能力

模块已具备实际运行能力。在正常配置下，引擎可以从配置应用到学生生成、窗口初始化、事件生成完成完整的启动流程。与前端通过 `SimulationConfigRequest` 的对接接口已就绪。

### 8.3 改进建议

1. **建议增加极端大值校验**：在 `deriveMealPopulations()` 和 `generateArrivalPlan()` 中增加对 totalPopulation 合理上限的校验，避免在接收极端大值时产生整数溢出（参考 TC-A-E03 的失败分析）。

2. **建议增加日志输出**：当前模块缺少运行日志，建议在关键步骤（人数分配、峰值创建、学生生成）增加 INFO 级别日志，便于生产环境问题排查。

3. **建议优化 FULL_DAY 模式的总人数语义**：当前实现中，各餐段人数独立从同一个 totalPopulation 推导，导致 FULL_DAY 模式下生成的学生总数大于输入的总人数（如 totalPopulation=1000 时实际生成 1287 人）。建议在文档中明确说明"总人数指每餐段独立计算的基准人数"，或提供跨餐段去重的可选逻辑。

---

*测试执行环境：JDK 25, Windows 11*  
*测试数据：模拟数据*  
*随机种子：20260324L*

---

## 附录A 测试代码说明

### A.1 测试代码目录说明

所有测试代码位于以下目录：

```
C:\Users\HS\IdeaProjects\cafeteria-system\Canteen-Simulation\src\testAll\A
```

该目录包含 5 个独立测试类文件和 1 个统一测试入口文件：

| 文件名 | 测试内容说明 | 对应测试用例编号 |
|--------|-------------|-----------------|
| `InitPopulationTest.java` | 测试 deriveMealPopulations 方法在不同总人数下的人数分配正确性、层级关系和比例合理性 | TC-A-001 ~ TC-A-003 |
| `ArrivalTimeTest.java` | 测试各餐段到达时间范围、FULL_DAY 模式时间连续性和到达时间排序正确性 | TC-A-004 ~ TC-A-006, TC-A-L03 ~ TC-A-L05 |
| `PersonInitTest.java` | 测试学生对象字段完整性、ID 唯一性、同组一致性和人群类型分布 | TC-A-007 ~ TC-A-009, TC-A-014, TC-A-L06 |
| `BoundaryTest.java` | 测试非法输入异常处理、null 参数默认行为、配置校验和构造函数 | TC-A-B01 ~ TC-A-B07, TC-A-E01 ~ TC-A-E09 |
| `InitModuleIntegrationTest.java` | 测试 SINGLE_PERIOD 和 FULL_DAY 模式集成流程、统计完整性、事件生成和引擎启动 | TC-A-010 ~ TC-A-013, TC-A-015 ~ TC-A-017 |
| `AllInitTests.java` | 一键运行上述全部 5 个测试类，汇总各测试类的 PASS/FAIL 结果 | 全部 40 个测试用例 |

每个测试类均可独立运行（包含独立的 main 方法），同时支持通过 `AllInitTests.java` 一键运行全部测试。

### A.2 AllInitTests.java —— 统一测试入口

以下为统一测试入口的核心代码。该文件依次调用 5 个测试类的 runAll 方法，并累计各测试类的失败数，最终输出汇总结论：

```java
public class AllInitTests {

    private static int totalFail = 0;

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("  A 模块 - 初始化模块全部单元测试");
        System.out.println("  包含:");
        System.out.println("    1. InitPopulationTest");
        System.out.println("    2. ArrivalTimeTest");
        System.out.println("    3. PersonInitTest");
        System.out.println("    4. BoundaryTest");
        System.out.println("    5. InitModuleIntegrationTest");
        System.out.println("==========================================");

        CanteenConfig.resetToDefaults();

        // 依次运行 5 个测试类
        int fail1 = InitPopulationTest.runAll();
        accumulate("InitPopulationTest", fail1);
        CanteenConfig.resetToDefaults();

        int fail2 = ArrivalTimeTest.runAll();
        accumulate("ArrivalTimeTest", fail2);
        CanteenConfig.resetToDefaults();

        int fail3 = PersonInitTest.runAll();
        accumulate("PersonInitTest", fail3);
        CanteenConfig.resetToDefaults();

        int fail4 = BoundaryTest.runAll();
        accumulate("BoundaryTest", fail4);
        CanteenConfig.resetToDefaults();

        int fail5 = InitModuleIntegrationTest.runAll();
        accumulate("InitModuleIntegrationTest", fail5);

        // 最终汇总
        if (totalFail == 0) {
            System.out.println("  结论: [全部测试通过]");
        } else {
            System.out.println("  结论: [存在 " + totalFail
                    + " 个失败项，请检查上方输出]");
        }
    }

    private static void accumulate(
            String testName, int failCount) {
        totalFail += failCount;
        System.out.println("  >>> " + testName
                + " 完成 (failures=" + failCount + ")");
    }
}
```

### A.3 InitPopulationTest.java —— 人口分配测试关键代码

该测试类验证 `deriveMealPopulations` 方法的核心逻辑。以下为测试汇总输出的关键代码段，每个测试方法在运行后打印各自的 PASS/FAIL 结果，最终由 printSummary 统一汇总：

```java
public class InitPopulationTest {

    private static int testPassCount = 0;
    private static int testFailCount = 0;

    public static int runAll() {
        testPassCount = 0;
        testFailCount = 0;
        CanteenConfig.resetToDefaults();

        // 正常数据测试
        testDeriveMealPopulations_Normal("正常-100人", 100);
        testDeriveMealPopulations_Normal("正常-1000人", 1000);
        testDeriveMealPopulations_Normal("正常-3000人", 3000);

        // 边界数据测试
        testDeriveMealPopulations_Normal("边界-1人", 1);
        testDeriveMealPopulations_Normal("边界-10人", 10);

        // 大规模数据测试
        testDeriveMealPopulations_Normal("大规模-20000人", 20000);
        testDeriveMealPopulations_Normal("大规模-50000人", 50000);

        // 层级关系测试
        testHierarchyRule("层级-1000人", 1000);
        testHierarchyRule("层级-3000人", 3000);
        testHierarchyRule("层级-50000人", 50000);

        // 比例合理性测试
        testRatioReasonableness("比例-1000人", 1000);
        testRatioReasonableness("比例-20000人", 20000);

        return testFailCount;
    }

    private static void printSummary() {
        System.out.println("==========================================");
        System.out.println("  InitPopulationTest 测试汇总");
        System.out.println("  PASS: " + testPassCount
                + "  FAIL: " + testFailCount);
        if (testFailCount == 0) {
            System.out.println("  结果: [全部通过]");
        } else {
            System.out.println("  结果: [存在失败]");
        }
        System.out.println("==========================================");
    }
}
```

### A.4 ArrivalTimeTest.java —— 到达时间测试关键代码

该测试类验证到达时间生成和 FULL_DAY 模式的时间连续性。以下为测试 FULL_DAY 模式下到达时间排序正确性的代码，验证学生列表是否严格按到达时间升序排列：

```java
private static void testArrivalTimeSorted(
        String testName, int totalPopulation, MealPeriod period) {

    System.out.println("[" + testName + "]");
    String modeLabel = (period == null) ? "FULL_DAY" : period.getCode();
    System.out.println("输入数据：总人数=" + totalPopulation
            + ", 模式=" + modeLabel);

    ArrivalModule module = new ArrivalModule(20260324L);
    SimulationMode mode = (period == null)
            ? SimulationMode.FULL_DAY
            : SimulationMode.SINGLE_PERIOD;
    MealPeriod p = (period == null) ? MealPeriod.BREAKFAST : period;

    ArrivalGenerationResult result = module.generateArrivalPlan(
            totalPopulation, mode, p);
    List<Student> students = result.getStudents();

    // 验证排序正确性
    boolean sorted = true;
    long prevTime = Long.MIN_VALUE;
    int violations = 0;
    for (Student s : students) {
        if (s.getArrivalTime() < prevTime) {
            sorted = false;
            violations++;
        }
        prevTime = s.getArrivalTime();
    }

    System.out.println("实际结果：总人数=" + students.size()
            + ", 排序违规数=" + violations);

    if (sorted) {
        System.out.println("[PASS] 学生到达时间按升序排列正确");
        testPassCount++;
    } else {
        System.out.println("[FAIL] 存在 " + violations + " 处排序错误");
        testFailCount++;
    }
}
```

### A.5 PersonInitTest.java —— 人员对象生成测试关键代码

该测试类验证学生对象字段完整性。以下为同组学生一致性验证的代码，测试同一 groupId 的学生是否具有相同的 arrivalTime 和 mealPeriod：

```java
private static void testSameGroupConsistency(
        String testName, int totalPopulation, MealPeriod period) {

    System.out.println("[" + testName + "]");
    System.out.println("输入数据：总人数=" + totalPopulation);

    ArrivalModule module = new ArrivalModule(20260324L);
    ArrivalGenerationResult result = module.generateArrivalPlan(
            totalPopulation, SimulationMode.SINGLE_PERIOD, period);
    List<Student> students = result.getStudents();

    // 按 groupId 分组
    Map<Integer, List<Student>> grouped =
            module.groupStudentsByGroupId(students);

    int groupsOK = 0, groupsWithErrors = 0;
    for (Map.Entry<Integer, List<Student>> entry
            : grouped.entrySet()) {
        List<Student> members = entry.getValue();
        if (members.isEmpty()) continue;

        long firstTime = members.get(0).getArrivalTime();
        MealPeriod firstPeriod = members.get(0).getMealPeriod();
        boolean groupOk = true;

        for (Student s : members) {
            if (s.getArrivalTime() != firstTime
                    || s.getMealPeriod() != firstPeriod) {
                groupOk = false;
                break;
            }
        }
        if (groupOk) groupsOK++;
        else groupsWithErrors++;
    }

    System.out.println("实际结果：总组数=" + grouped.size()
            + ", 一致组数=" + groupsOK
            + ", 不一致组数=" + groupsWithErrors);

    if (groupsWithErrors == 0) {
        System.out.println("[PASS] 所有组内学生属性一致");
        testPassCount++;
    } else {
        System.out.println("[FAIL] 存在 " + groupsWithErrors
                + " 个组内部不一致");
        testFailCount++;
    }
}
```

### A.6 BoundaryTest.java —— 边界与异常测试关键代码

该测试类验证模块对各类非法输入的防御能力。以下为 null SimulationConfigRequest 参数测试的代码，验证传入 null 配置时是否抛出异常：

```java
private static void testNullConfigRequest(String testName) {

    System.out.println("[" + testName + "]");
    System.out.println("输入数据：SimulationConfigRequest = null");
    System.out.println("预期结果：抛出 IllegalArgumentException");

    CanteenConfig.resetToDefaults();

    try {
        CanteenConfig.updateAllConfigs(null);
        System.out.println("实际结果：没有抛出异常");
        System.out.println("[FAIL] null config request 应抛出异常");
        testFailCount++;
    } catch (IllegalArgumentException e) {
        System.out.println("实际结果：抛出 IllegalArgumentException: "
                + e.getMessage());
        System.out.println("[PASS] 正确抛出 IllegalArgumentException");
        testPassCount++;
    }
}
```

### A.7 InitModuleIntegrationTest.java —— 集成测试关键代码

该测试类验证模块间的集成流程。以下为 CanteenSimulationEngine 默认配置启动的集成测试代码，验证引擎从配置到启动的完整流程：

```java
private static void testSimulationEngine(String testName) {

    System.out.println("[" + testName + "]");
    System.out.println("输入数据：默认配置 (总人数="
            + CanteenConfig.DEFAULT_TOTAL_POPULATION + ")");
    System.out.println("预期结果：引擎正常启动，"
            + "生成学生、窗口、桌位、事件");

    try {
        CanteenSimulationEngine engine =
                new CanteenSimulationEngine();
        engine.startEngine();

        System.out.println("实际结果：");
        System.out.println("  学生数: " + engine.getStudentCount());
        System.out.println("  窗口数: " + engine.getWindowCount());
        System.out.println("  桌位数: " + engine.getTableCount());
        System.out.println("  事件数: " + engine.getEventQueue().size());

        if (engine.getStudentCount() > 0) {
            System.out.println("[PASS] 引擎启动后生成了学生");
            testPassCount++;
        } else {
            System.out.println("[FAIL] 未生成任何学生");
            testFailCount++;
        }

        // 清理
        engine.stopEngine();
        engine.resetEngine();
    } catch (Exception e) {
        System.out.println("[FAIL] 引擎启动不应抛出异常: "
                + e.getMessage());
        testFailCount++;
    }
}
```

### A.8 测试运行说明

1. 所有测试代码位于目录：`Canteen-Simulation/src/testAll/A`
2. 每个测试类均可独立运行（各自包含 main 方法），例如：
   - `java testAll.A.InitPopulationTest`
   - `java testAll.A.ArrivalTimeTest`
3. 支持通过 `AllInitTests.java` 一键运行全部 5 个测试类：
   - `java testAll.A.AllInitTests`
4. 测试使用固定随机种子 20260324L，所有结果均可稳定复现
5. 每个测试类在运行前均调用 `CanteenConfig.resetToDefaults()` 确保配置环境一致
6. 测试均在实际代码环境下编译运行通过，输出结果与报告记录一致
