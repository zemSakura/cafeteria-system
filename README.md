# Canteen-Simulation

食堂仿真系统，Java Swing 桌面应用。项目支持从源码直接部署运行，适合从 GitHub 下载后在 Windows PowerShell 中启动。

## 运行环境

- Windows 10/11 64 位
- JDK 17 或 JDK 21，已验证 `java` 和 `javac` 命令可用
- 首次运行需要网络下载 FlatLaf 依赖；离线运行时请提前把 `flatlaf-3.4.1.jar` 放到项目根目录的 `lib` 文件夹

PowerShell 中检查 JDK：

~~~powershell
$env:JAVA_HOME
java -version
javac -version
~~~

注意：PowerShell 使用 `$env:JAVA_HOME`，不要使用 cmd 的 `%JAVA_HOME%`。

## GitHub 下载后直接运行

最简单方式：双击项目根目录下的：

~~~text
Canteen-Simulation.exe
~~~

它会自动调用 `run.ps1`，完成编译、部署并启动系统主界面。

如果希望在 PowerShell 中手动运行，也可以进入项目根目录执行：

~~~powershell
cd cafeteria-system
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1
~~~

`run.ps1` 会自动执行以下动作：

1. 检查项目结构和 JDK。
2. 准备 FlatLaf 依赖。
3. 编译 `Canteen-Simulation\src` 下的源码。
4. 生成 `deploy-output` 运行目录。
5. 启动系统主界面。

## 只生成部署包

~~~powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy.ps1 -Clean
~~~

生成内容位于 `deploy-output`：

- `run.bat`：Windows 双击启动脚本
- `run.ps1`：PowerShell 启动脚本
- `Canteen-Simulation.jar`：可选 JAR 运行包
- `docs`：部署说明、使用手册、流程图和缺失项检查表

## 入口类

- 源码启动入口：`backend.Main`
- 实际图形界面入口：`frontend.MainDashboard`
- UI 技术：Swing
- 依赖管理：普通 Java 工程，无 Maven/Gradle
- 数据库：当前不需要数据库

