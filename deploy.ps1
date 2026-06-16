param(
    [string]$ProjectRoot = $PSScriptRoot,
    [string]$OutputDir = (Join-Path $PSScriptRoot "deploy-output"),
    [switch]$NoDownload,
    [switch]$Clean,
    [switch]$NoPackage
)

$ErrorActionPreference = "Stop"

$ProjectName = "Canteen-Simulation"
$FlatLafVersion = "3.4.1"
$FlatLafFileName = "flatlaf-$FlatLafVersion.jar"
$MainClass = "backend.Main"
$UiMainClass = "frontend.MainDashboard"
$SourceRoot = Join-Path $ProjectRoot "Canteen-Simulation\src"
$ClassesDir = Join-Path $OutputDir "classes"
$LibDir = Join-Path $OutputDir "lib"
$BuildDir = Join-Path $OutputDir "build"
$DocsDir = Join-Path $OutputDir "docs"
$StartupDir = Join-Path $OutputDir "startup"
$JarFile = Join-Path $OutputDir "Canteen-Simulation.jar"
$InfoFile = Join-Path $OutputDir "deployment-info.txt"
$RunBat = Join-Path $OutputDir "run.bat"
$RunPs1 = Join-Path $OutputDir "run.ps1"
$RunJarBat = Join-Path $StartupDir "run-jar.bat"
$RunJarPs1 = Join-Path $StartupDir "run-jar.ps1"
$PackageFile = Join-Path $ProjectRoot "软件综合实训_[分组编号]_系统部署程序.zip"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message"
}

function Fail {
    param([string]$Message)
    Write-Host ""
    Write-Host "[ERROR] $Message" -ForegroundColor Red
    exit 1
}

function Get-NativeCommandOutput {
    param([string]$CommandLine)
    return (cmd /c "$CommandLine 2>&1") -join "`n"
}

function Get-JavaMajorVersion {
    param([string]$VersionText)

    if ($VersionText -match 'version "1\.(\d+)') {
        return [int]$Matches[1]
    }
    if ($VersionText -match 'version "(\d+)') {
        return [int]$Matches[1]
    }
    if ($VersionText -match '^javac\s+(\d+)') {
        return [int]$Matches[1]
    }
    if ($VersionText -match '^(\d+)') {
        return [int]$Matches[1]
    }
    return $null
}

function Resolve-FlatLafJar {
    $candidates = @(
        (Join-Path $ProjectRoot "lib\$FlatLafFileName"),
        (Join-Path $LibDir $FlatLafFileName)
    )

    if ($env:USERPROFILE) {
        $candidates += Join-Path $env:USERPROFILE ".m2\repository\com\formdev\flatlaf\$FlatLafVersion\$FlatLafFileName"
    }

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    return $null
}

function Get-RelativePath {
    param(
        [string]$BasePath,
        [string]$TargetPath
    )

    $baseUri = New-Object System.Uri(($BasePath.TrimEnd('\') + '\'))
    $targetUri = New-Object System.Uri($TargetPath)
    return [System.Uri]::UnescapeDataString($baseUri.MakeRelativeUri($targetUri).ToString()).Replace('/', '\')
}

function Write-Utf8File {
    param(
        [string]$Path,
        [string]$Content
    )

    $parent = Split-Path -Parent $Path
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    Set-Content -LiteralPath $Path -Value $Content -Encoding UTF8
}

function New-DeploymentDocs {
    param(
        [string]$JavaVersionText,
        [string]$JavacVersionText,
        [string[]]$MainMethods,
        [bool]$HasDatabaseDependency,
        [string]$BuildMode,
        [string]$UiTechnology,
        [int]$JavaSourceCount,
        [string]$GeneratedAt
    )

    $mainMethodText = if ($MainMethods.Count -gt 0) { ($MainMethods -join "`n") } else { "未扫描到 main 方法" }
    $databaseText = if ($HasDatabaseDependency) { "存在疑似数据库相关代码，需要进一步确认数据库驱动与连接配置。" } else { "未发现 java.sql、JDBC、数据库连接字符串或数据库驱动依赖，当前部署不需要数据库。" }

    $deployDoc = @"
# [分组编号] 系统部署环境搭建说明

## 一、系统部署环境要求

项目名称：$ProjectName

项目类型：Java 桌面应用程序

部署方式：源码部署优先，已同时生成可选 JAR 运行方式。

系统说明：本系统用于模拟学生到达、排队、取餐、就座、离开等离散事件过程，并提供统计分析、可视化展示和资源配置优化功能。

## 二、软硬件配置说明

| 类别 | 推荐配置 | 最低配置 |
| --- | --- | --- |
| 操作系统 | Windows 10/11 64 位 | Windows 10 64 位 |
| CPU | 2 核及以上 | 2 核 |
| 内存 | 8GB | 4GB |
| 磁盘 | 30GB 以上剩余空间 | 30GB |
| JDK | JDK 17 或 JDK 21 | JDK 17 |
| IDE | IntelliJ IDEA | IntelliJ IDEA |
| 数据库 | 不需要 | 不需要 |
| 网络 | 首次下载 FlatLaf 依赖时需要 | 离线时需提前准备 lib\$FlatLafFileName |

当前部署程序检测到：

~~~text
$JavaVersionText

$JavacVersionText
~~~

## 三、虚拟机搭建过程

1. 在 VMware 中新建 Windows 虚拟机。
2. CPU 设置为 2 核或以上，内存设置为 4GB 或以上，磁盘空间设置为 30GB 或以上。
3. 安装 Windows 10/11 64 位系统。
4. 进入系统后确认网络可用，用于安装 JDK、IntelliJ IDEA 和首次下载依赖。

## 四、JDK 安装与配置

1. 安装 JDK 17 或 JDK 21。
2. 配置系统环境变量 JAVA_HOME，值为 JDK 安装目录，例如 C:\Program Files\Java\jdk-21。
3. 将 %JAVA_HOME%\bin 加入系统 Path。
4. 在 PowerShell 中使用以下命令验证：

~~~powershell
`$env:JAVA_HOME
java -version
javac -version
~~~

注意：PowerShell 中查看环境变量使用 `$env:JAVA_HOME，不要使用 cmd 的 %JAVA_HOME% 写法。

## 五、IDEA 安装与配置

1. 安装 IntelliJ IDEA。
2. 打开项目根目录：$ProjectRoot。
3. 在 File > Project Structure > Project SDK 中选择已安装的 JDK 17 或 JDK 21。
4. 确认源码目录为 Canteen-Simulation\src。
5. 项目当前未使用 Maven 或 Gradle，依赖以 IntelliJ Library / 本地 Jar 方式管理。

## 六、项目导入过程

1. 使用 IDEA 打开 cafeteria-system 根目录。
2. 等待 IDEA 索引项目源码。
3. 检查 .iml 中包含的源码目录：Canteen-Simulation\src。
4. 检查 FlatLaf 依赖是否可用。部署程序会自动把 $FlatLafFileName 放入 deploy-output\lib。

## 七、系统启动过程

源码运行入口：

~~~text
$MainClass
~~~

实际图形界面入口：

~~~text
$UiMainClass
~~~

扫描到的 main 方法：

~~~text
$mainMethodText
~~~

部署后启动方式：

~~~powershell
.\deploy-output\run.ps1
~~~

或双击：

~~~text
deploy-output\run.bat
~~~

可选 JAR 方式：

~~~powershell
.\deploy-output\startup\run-jar.ps1
~~~

## 八、功能验证过程

1. 启动系统后确认主窗口正常显示。
2. 在控制面板中检查就餐人数、仿真时长、窗口数、餐桌数等参数。
3. 点击开始仿真，观察排队区、就餐区、等待座位区、离开区状态变化。
4. 检查 KPI 面板、趋势图和状态流是否随仿真推进更新。
5. 进入优化界面，设置窗口数/餐桌数范围，执行自动寻优。
6. 查看输出目录中是否生成仿真报告、CSV 或优化结果文件。

## 九、常见问题与解决方案

| 问题 | 原因 | 解决方案 |
| --- | --- | --- |
| java 无法识别 | 未安装 JDK 或 Path 未配置 | 安装 JDK 17/21，并将 `$env:JAVA_HOME\bin 加入 Path |
| javac 无法识别 | 只安装了 JRE | 安装完整 JDK |
| 编译找不到 FlatLaf | 缺少外观依赖 | 联网运行部署程序，或将 $FlatLafFileName 放入 lib 目录后使用 -NoDownload |
| 中文界面乱码 | 控制台编码或源码编码不一致 | 保持源码 UTF-8，部署程序使用 javac -encoding UTF-8 编译 |
| 双击脚本无反应 | PowerShell 执行策略限制 | 使用 deploy.bat 或执行 powershell -ExecutionPolicy Bypass -File .\deploy.ps1 |
| 程序启动但无界面 | JDK 或图形桌面环境异常 | 使用 Windows 桌面环境运行，不要在无图形环境中运行 Swing 主界面 |

## 十、当前代码扫描结论

| 检查项 | 结果 |
| --- | --- |
| 源码目录 | Canteen-Simulation\src |
| Java 源文件数 | $JavaSourceCount |
| 依赖管理 | $BuildMode |
| UI 技术 | $UiTechnology |
| JavaFX | 未发现 |
| Swing | 已发现 |
| 数据库依赖 | $databaseText |
| 第三方依赖 | FlatLaf $FlatLafVersion |
| 推荐部署方式 | 先源码运行，再使用部署程序生成运行目录 |
"@

    $manualDoc = @"
# [分组编号] 系统使用手册

## 一、如何启动进入系统

部署完成后进入 deploy-output 目录，双击 run.bat，或在 PowerShell 中执行：

~~~powershell
.\deploy-output\run.ps1
~~~

系统主入口为 $MainClass，该入口会调用 $UiMainClass 打开 Swing 图形界面。

## 二、主界面功能说明

系统主界面包含参数控制区、仿真可视化区、KPI 指标区、趋势图、优化分析区等模块。

| 功能区域 | 用途 |
| --- | --- |
| 控制面板 | 设置仿真人数、时长、窗口数、餐桌数、优化范围等 |
| 排队区 | 展示各取餐窗口队列长度和压力状态 |
| 就餐区 | 展示餐桌使用情况和座位占用情况 |
| 等待座位区 | 展示取餐后等待座位的人数和压力 |
| 离开区 | 展示完成就餐或放弃就餐的人数 |
| KPI 面板 | 展示完成率、等待时间、窗口利用率、收入等统计指标 |
| 趋势图 | 展示仿真过程中的队列、等待、服务等变化趋势 |
| 优化面板 | 根据窗口数、餐桌数范围自动寻找较优资源配置 |

## 三、系统参数配置

| 参数 | 默认值/来源 | 说明 |
| --- | --- | --- |
| totalStudents / TOTAL_POPULATION | 1000 | 预计就餐总人数 |
| windowCount | 5 | 取餐窗口数量，后端由 WINDOW_DISTANCES 和 WINDOW_AVG_SERVE_TIME 长度决定 |
| tableCount / TOTAL_TABLES | UI 默认 30，后端默认 150 | 餐桌数量，部署文档以界面可配置值为操作入口 |
| openDuration | 120 分钟 | 单个餐段开放时长 |
| randomSeed | 20260407 / 20260324 | 随机种子，用于结果复现 |
| windowDistances | 10, 15, 20, 25, 30 | 各窗口距离参数 |
| windowAvgServeTime | 18, 21, 24, 19, 90 秒 | 各窗口平均服务时间 |
| diningTimeMean | 900 秒 | 平均就餐时间，约 15 分钟 |
| diningTimeStd | 180 秒 | 就餐时间标准差 |
| minDiningTime | 300 秒 | 最短就餐时间 |
| patienceMin | 1200 秒 | 最短等待耐心阈值，约 20 分钟 |
| patienceMax | 2700 秒 | 最长等待耐心阈值，约 45 分钟 |
| probSolo | 0.7 | 单人就餐概率 |
| probDuo | 0.15 | 双人组概率 |
| probTrio | 0.05 | 三人组概率 |
| probTeam | 0.1 | 团队组概率 |
| simulationMode | fullDay / singlePeriod | 全天三餐或单餐段仿真 |
| mealPeriod | breakfast/lunch/dinner | 单餐段模式下选择早/中/晚餐 |
| breakfastPopulationRatio | 0.25 | 早餐人数比例 |
| lunchPopulationRatio | 0.45 | 午餐人数比例 |
| dinnerPopulationRatio | 0.30 | 晚餐人数比例 |
| avgMealPrice | 15.0 | 平均客单价 |
| windowCostPerHour | 35.0 | 单窗口小时成本 |
| tableCost | 0.5 | 单桌成本 |
| lostStudentPenalty | 12.0 | 放弃就餐损失 |
| repeatTimes | 3 | 优化候选重复运行次数 |
| maxCandidateEvaluations | 400 | 优化候选上限 |
| localRestartCount | 3 | 局部搜索重启次数 |
| topK | 10 | 保留的优化方案数量 |

## 四、各功能如何使用

1. 参数配置：在左侧控制面板输入预计就餐人数、仿真时长、窗口数和餐桌数。
2. 开始仿真：点击“开始仿真”后，系统会按照当前参数生成学生到达计划并推动离散事件。
3. 停止仿真：仿真过程中可点击停止按钮终止当前运行。
4. 自动寻优：进入优化界面，设置窗口数和餐桌数范围，运行后查看推荐方案。
5. 应用推荐：从优化结果中选择方案后，可回到仿真界面复用该资源配置。
6. 查看结果：仿真结束后查看 KPI、趋势图、统计分析结果和输出文件。

## 五、需要配置的系统参数

普通运行主要配置：就餐人数、仿真时长、窗口数、餐桌数、单人概率、餐段模式、随机种子。

优化运行主要配置：窗口范围、餐桌范围、当前资源配置、重复次数、候选上限、TopK 数量、成本参数和惩罚权重。

高级参数一般保持默认即可，只有在实验复现、论文数据对比或资源寻优时才需要调整。
"@

    $flowDoc = @"
# [分组编号] 系统部署流程图

~~~mermaid
flowchart TD
    A["创建 VMware Windows 虚拟机"] --> B["安装 Windows 10/11 64 位"]
    B --> C["安装 JDK 17 或 JDK 21"]
    C --> D["配置 JAVA_HOME 和 Path"]
    D --> E["执行 java -version 与 javac -version"]
    E --> F["安装 IntelliJ IDEA"]
    F --> G["导入 cafeteria-system 项目"]
    G --> H["确认源码目录 Canteen-Simulation/src"]
    H --> I["确认 FlatLaf 依赖"]
    I --> J["定位启动类 backend.Main"]
    J --> K["运行 deploy.bat 或 deploy.ps1"]
    K --> L["编译源码并生成 deploy-output"]
    L --> M["生成 run.bat / run.ps1 / JAR"]
    M --> N["启动系统主界面"]
    N --> O["验证参数配置、仿真运行、统计分析、自动寻优"]
~~~
"@

    $checkDoc = @"
# 部署缺失项检查表

生成时间：$GeneratedAt

| 检查项 | 当前结果 | 处理建议 |
| --- | --- | --- |
| Windows 10/11 64 位 | 需在目标虚拟机确认 | 使用 winver 查看 |
| JDK | 已由部署程序检查 | 推荐 JDK 17 或 JDK 21 |
| java -version | 已执行 | 若失败，检查 Path |
| javac -version | 已执行 | 若失败，安装完整 JDK |
| `$env:JAVA_HOME | 需人工确认 | PowerShell 中执行 `$env:JAVA_HOME |
| IntelliJ IDEA | 需人工确认 | 源码运行方式需要 |
| Maven/Gradle | 未发现 | 当前按普通 Java 工程部署 |
| 数据库 | $databaseText | 无需安装 MySQL/SQLite 等数据库 |
| FlatLaf 依赖 | 部署程序已准备 | 离线时放入 lib\$FlatLafFileName |
| 启动类 | $MainClass | 该类转发到 $UiMainClass |
| Swing/JavaFX | $UiTechnology / 未发现 JavaFX | 运行环境需支持桌面图形界面 |
| 启动脚本 | 已生成 | deploy-output\run.bat、deploy-output\run.ps1 |
| JAR 包 | 已生成 | deploy-output\Canteen-Simulation.jar |
| 文档 | 已生成 | deploy-output\docs |
"@

    $quickDoc = @"
# 安装部署简要说明

1. 在 Windows 虚拟机中安装 JDK 17 或 JDK 21。
2. 配置 `$env:JAVA_HOME，并将 JDK 的 bin 目录加入 Path。
3. 在项目根目录执行：

~~~powershell
.\deploy.bat
~~~

或：

~~~powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy.ps1
~~~

4. 部署完成后运行：

~~~powershell
.\deploy-output\run.ps1
~~~

5. 若虚拟机不能联网，请提前将 $FlatLafFileName 放入项目根目录的 lib 文件夹，再执行：

~~~powershell
.\deploy.ps1 -NoDownload
~~~
"@

    Write-Utf8File (Join-Path $DocsDir "[分组编号]_系统部署环境搭建说明.md") $deployDoc
    Write-Utf8File (Join-Path $DocsDir "[分组编号]_系统使用手册.md") $manualDoc
    Write-Utf8File (Join-Path $DocsDir "部署流程图.md") $flowDoc
    Write-Utf8File (Join-Path $DocsDir "部署缺失项检查表.md") $checkDoc
    Write-Utf8File (Join-Path $OutputDir "安装部署简要说明.md") $quickDoc
}

Write-Host "Canteen Simulation System Deployment Program"
Write-Host "Project root : $ProjectRoot"
Write-Host "Output dir   : $OutputDir"

Write-Step "Checking project structure"
if (-not (Test-Path -LiteralPath $SourceRoot)) {
    Fail "Source directory not found: $SourceRoot"
}

$backendDir = Join-Path $SourceRoot "backend"
$frontendDir = Join-Path $SourceRoot "frontend"
foreach ($dir in @($backendDir, $frontendDir)) {
    if (-not (Test-Path -LiteralPath $dir)) {
        Fail "Required source directory not found: $dir"
    }
}

$javaFiles = Get-ChildItem -LiteralPath $SourceRoot -Recurse -File -Filter "*.java" |
    Where-Object { $_.FullName -notmatch '\\testAll\\' }

if ($javaFiles.Count -eq 0) {
    Fail "No Java source files were found."
}

$mainMethods = @(
    $javaFiles |
        Select-String -Pattern 'public\s+static\s+void\s+main\s*\(' |
        ForEach-Object {
            $relative = Get-RelativePath $ProjectRoot $_.Path
            "${relative}:$($_.LineNumber): $($_.Line.Trim())"
        }
)

if ($mainMethods.Count -eq 0) {
    Fail "No main method was found in current source code."
}

$mainClassFile = Join-Path $ClassesDir "backend\Main.class"
$swingHits = @($javaFiles | Select-String -Pattern 'javax\.swing|SwingUtilities|JFrame')
$javaFxHits = @($javaFiles | Select-String -Pattern 'javafx|extends\s+Application')
$dbHits = @($javaFiles | Select-String -Pattern 'java\.sql|DriverManager|jdbc:|DataSource|sqlite|mysql|postgres|h2database|mongodb')
$allProjectFiles = @(Get-ChildItem -LiteralPath $ProjectRoot -Recurse -File -ErrorAction SilentlyContinue)
$mavenFiles = @($allProjectFiles | Where-Object { $_.Name -eq "pom.xml" })
$gradleFiles = @($allProjectFiles | Where-Object { $_.Name -in @("build.gradle", "settings.gradle", "gradlew", "gradlew.bat") })

$buildMode = if ($mavenFiles.Count -gt 0) {
    "Maven"
} elseif ($gradleFiles.Count -gt 0) {
    "Gradle"
} else {
    "IntelliJ IDEA 普通 Java 工程，无 Maven/Gradle"
}

$uiTechnology = if ($javaFxHits.Count -gt 0) {
    "JavaFX/Swing mixed or JavaFX candidates found"
} elseif ($swingHits.Count -gt 0) {
    "Swing desktop UI"
} else {
    "No desktop UI framework detected"
}

Write-Host "Java source files : $($javaFiles.Count)"
Write-Host "Main class        : $MainClass"
Write-Host "UI technology     : $uiTechnology"
Write-Host "Build mode        : $buildMode"
Write-Host "Database required : $($dbHits.Count -gt 0)"

Write-Step "Checking JDK"
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
$javacCmd = Get-Command javac -ErrorAction SilentlyContinue
$jarCmd = Get-Command jar -ErrorAction SilentlyContinue

if (-not $javaCmd) {
    Fail "java was not found. Install JDK 17 or JDK 21 and add its bin directory to Path."
}
if (-not $javacCmd) {
    Fail "javac was not found. A JRE is not enough; install a full JDK 17 or JDK 21."
}
if (-not $jarCmd) {
    Fail "jar was not found. Install a full JDK 17 or JDK 21."
}

$javaVersionText = Get-NativeCommandOutput "java -version"
$javacVersionText = Get-NativeCommandOutput "javac -version"
$javacMajor = Get-JavaMajorVersion $javacVersionText

Write-Host $javaVersionText
Write-Host $javacVersionText

if ($null -eq $javacMajor) {
    Fail "Unable to parse javac version."
}
if ($javacMajor -lt 17) {
    Fail "JDK version is too old. Current javac major version is $javacMajor; required version is 17 or later."
}
if ($javacMajor -gt 21) {
    Write-Host "[WARN] Recommended JDK versions are 17 and 21. Current JDK is newer: $javacMajor." -ForegroundColor Yellow
}

Write-Step "Preparing output directories"
if ($Clean -and (Test-Path -LiteralPath $OutputDir)) {
    Remove-Item -LiteralPath $OutputDir -Recurse -Force
}

foreach ($generatedDir in @($ClassesDir, $BuildDir, $DocsDir, $StartupDir)) {
    if (Test-Path -LiteralPath $generatedDir) {
        Remove-Item -LiteralPath $generatedDir -Recurse -Force
    }
}

New-Item -ItemType Directory -Force -Path $ClassesDir | Out-Null
New-Item -ItemType Directory -Force -Path $LibDir | Out-Null
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null
New-Item -ItemType Directory -Force -Path $DocsDir | Out-Null
New-Item -ItemType Directory -Force -Path $StartupDir | Out-Null

Write-Step "Preparing FlatLaf dependency"
$flatlafSource = Resolve-FlatLafJar
$flatlafTarget = Join-Path $LibDir $FlatLafFileName

if (-not $flatlafSource) {
    if ($NoDownload) {
        Fail "FlatLaf was not found. Put $FlatLafFileName into the project lib directory, or run without -NoDownload."
    }

    $downloadUrl = "https://repo1.maven.org/maven2/com/formdev/flatlaf/$FlatLafVersion/$FlatLafFileName"
    Write-Host "FlatLaf not found locally. Downloading: $downloadUrl"
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $downloadUrl -OutFile $flatlafTarget
        $flatlafSource = $flatlafTarget
    } catch {
        Fail "Failed to download FlatLaf. Check network access, or manually place $FlatLafFileName in the project lib directory."
    }
} else {
    $resolvedSource = (Resolve-Path -LiteralPath $flatlafSource).Path
    $resolvedTarget = if (Test-Path -LiteralPath $flatlafTarget) { (Resolve-Path -LiteralPath $flatlafTarget).Path } else { $flatlafTarget }
    if ($resolvedSource -ne $resolvedTarget) {
        Copy-Item -LiteralPath $flatlafSource -Destination $flatlafTarget -Force
    }
}

if (-not (Test-Path -LiteralPath $flatlafTarget)) {
    Fail "FlatLaf jar was not prepared correctly: $flatlafTarget"
}

Write-Host "FlatLaf jar: $flatlafTarget"

Write-Step "Compiling project"
$sourceListFile = Join-Path $BuildDir "sources.txt"
$javaFiles |
    Sort-Object FullName |
    ForEach-Object { $_.FullName } |
    Set-Content -LiteralPath $sourceListFile -Encoding UTF8

$javacArgs = @(
    "-encoding",
    "UTF-8",
    "--release",
    "17",
    "-cp",
    $flatlafTarget,
    "-d",
    $ClassesDir
)

$javacArgs += Get-Content -LiteralPath $sourceListFile
& javac @javacArgs
if ($LASTEXITCODE -ne 0) {
    Fail "Compilation failed."
}

if (-not (Test-Path -LiteralPath $mainClassFile)) {
    Fail "Compilation finished but the main class was not generated: $MainClass"
}

Write-Step "Generating optional JAR package"
$manifestFile = Join-Path $BuildDir "MANIFEST.MF"
$manifest = @"
Manifest-Version: 1.0
Main-Class: $MainClass
Class-Path: lib/$FlatLafFileName

"@
Set-Content -LiteralPath $manifestFile -Value $manifest -Encoding ASCII

Push-Location $ClassesDir
try {
    & jar cfm $JarFile $manifestFile .
    if ($LASTEXITCODE -ne 0) {
        Fail "JAR packaging failed."
    }
} finally {
    Pop-Location
}

Write-Step "Generating startup scripts"
$runBatContent = @"
@echo off
setlocal
set "APP_DIR=%~dp0"
set "CLASSES_DIR=%APP_DIR%classes"
set "LIB_DIR=%APP_DIR%lib"

java -cp "%CLASSES_DIR%;%LIB_DIR%\$FlatLafFileName" $MainClass
if errorlevel 1 (
    echo.
    echo Program exited with an error. Check your Java installation and deployment files.
    pause
)
"@
Set-Content -LiteralPath $RunBat -Value $runBatContent -Encoding ASCII

$runPs1Content = @"
`$ErrorActionPreference = "Stop"
`$AppDir = Split-Path -Parent `$MyInvocation.MyCommand.Path
`$ClassesDir = Join-Path `$AppDir "classes"
`$LibJar = Join-Path `$AppDir "lib\$FlatLafFileName"
java -cp "`$ClassesDir;`$LibJar" $MainClass
"@
Set-Content -LiteralPath $RunPs1 -Value $runPs1Content -Encoding ASCII

$runJarBatContent = @"
@echo off
setlocal
set "APP_DIR=%~dp0.."
java -jar "%APP_DIR%\Canteen-Simulation.jar"
if errorlevel 1 (
    echo.
    echo Program exited with an error. Check your Java installation and deployment files.
    pause
)
"@
Set-Content -LiteralPath $RunJarBat -Value $runJarBatContent -Encoding ASCII

$runJarPs1Content = @"
`$ErrorActionPreference = "Stop"
`$StartupDir = Split-Path -Parent `$MyInvocation.MyCommand.Path
`$AppDir = Split-Path -Parent `$StartupDir
java -jar (Join-Path `$AppDir "Canteen-Simulation.jar")
"@
Set-Content -LiteralPath $RunJarPs1 -Value $runJarPs1Content -Encoding ASCII

Write-Step "Generating deployment documents"
$now = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
New-DeploymentDocs `
    -JavaVersionText $javaVersionText `
    -JavacVersionText $javacVersionText `
    -MainMethods $mainMethods `
    -HasDatabaseDependency ($dbHits.Count -gt 0) `
    -BuildMode $buildMode `
    -UiTechnology $uiTechnology `
    -JavaSourceCount $javaFiles.Count `
    -GeneratedAt $now

Write-Step "Writing deployment information"
$info = @"
Cafeteria Simulation System Deployment
Generated at : $now
Project root : $ProjectRoot
Source root  : $SourceRoot
Output dir   : $OutputDir
Classes dir  : $ClassesDir
Library dir  : $LibDir
Docs dir     : $DocsDir
Main class   : $MainClass
UI class     : $UiMainClass
Dependency   : $FlatLafFileName
Build mode   : $buildMode
UI tech      : $uiTechnology
Database     : $($dbHits.Count -gt 0)

Java runtime:
$javaVersionText

Java compiler:
$javacVersionText

How to start:
1. Open this directory: $OutputDir
2. Double-click run.bat
3. Or run in PowerShell:
   .\run.ps1
4. Optional JAR startup:
   .\startup\run-jar.ps1

Important PowerShell note:
Use `$env:JAVA_HOME` in PowerShell. Do not use cmd-style %JAVA_HOME%.
"@
Write-Utf8File $InfoFile $info

if (-not $NoPackage) {
    Write-Step "Generating deployment package"
    if (Test-Path -LiteralPath $PackageFile) {
        Remove-Item -LiteralPath $PackageFile -Force
    }
    Compress-Archive -Path (Join-Path $OutputDir "*") -DestinationPath $PackageFile -Force
    Write-Host "Package file     : $PackageFile"
}

Write-Step "Deployment completed"
Write-Host "Output directory : $OutputDir"
Write-Host "Startup script   : $RunBat"
Write-Host "PowerShell start : $RunPs1"
Write-Host "Jar package      : $JarFile"
Write-Host "Docs directory   : $DocsDir"
Write-Host "Info file        : $InfoFile"
Write-Host ""
Write-Host "Run the system with:"
Write-Host "  $RunBat"





