# Canteen-Simulation 食堂仿真系统

本项目是一个基于 Java Swing 的食堂仿真系统，用于模拟学生到达、排队、取餐、就座、离开等过程，并提供统计分析、可视化展示和资源配置优化功能。

## 应该下载哪个文件

如果你只是想直接运行软件，请下载 Release 里的：

```text
Canteen-Simulation-Portable-Windows.zip
```

如果你想查看源码、修改代码或按源码方式部署，请下载：

```text
Source code (zip)
```

GitHub Release 页面通常会显示三个文件：

| 文件 | 用途 | 是否推荐普通用户下载 |
| --- | --- | --- |
| `Canteen-Simulation-Portable-Windows.zip` | 免配置运行包，解压后双击 exe 即可运行 | 推荐 |
| `Source code (zip)` | GitHub 自动生成的源码包，适合源码部署或代码检查 | 不推荐普通用户 |
| `Source code (tar.gz)` | GitHub 自动生成的源码包，通常给 Linux/macOS 用户或归档使用 | 不推荐普通用户 |

## 方式一：直接下载软件运行

适合普通用户、验收演示、虚拟机快速测试。

操作步骤：

1. 打开 GitHub 项目的 Releases 页面。
2. 下载 `Canteen-Simulation-Portable-Windows.zip`。
3. 解压整个压缩包。
4. 进入解压后的 `Canteen-Simulation` 文件夹。
5. 双击 `Canteen-Simulation.exe` 启动系统。

这个版本已经内置 Java Runtime，因此不需要安装 JDK/JRE，也不需要配置 `JAVA_HOME` 或系统 `Path`。

注意事项：

- 请完整解压后再运行，不要在压缩包预览窗口里直接打开 exe。
- 不要删除 `app`、`runtime` 等子目录。
- 推荐在 Windows 10/11 64 位系统中运行。
- 首次启动可能需要等待几秒钟。

## 方式二：下载源码运行

适合需要查看源码、修改代码、检查部署过程或进行二次开发的用户。

源码运行需要先安装并配置 JDK。

推荐环境：

| 项目 | 要求 |
| --- | --- |
| 操作系统 | Windows 10/11 64 位 |
| Java | JDK 17 或 JDK 21 |
| 网络 | 首次运行建议联网，用于下载 FlatLaf 依赖 |
| 数据库 | 不需要 |

PowerShell 中检查 JDK：

```powershell
$env:JAVA_HOME
java -version
javac -version
```

注意：PowerShell 使用 `$env:JAVA_HOME`，不要使用 cmd 的 `%JAVA_HOME%`。

源码下载后，在项目根目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1
```

`run.ps1` 会自动完成：

1. 检查项目结构和 JDK。
2. 准备 FlatLaf 依赖。
3. 编译 `Canteen-Simulation\src` 下的源码。
4. 生成 `deploy-output` 运行目录。
5. 启动系统主界面。

如果虚拟机不能联网，请提前将 `flatlaf-3.4.1.jar` 放入项目根目录的 `lib` 文件夹，然后执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -NoDownload
```

## 只生成源码部署目录

如果只想生成部署文件，不立即运行系统，可以执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy.ps1 -Clean
```

生成内容位于 `deploy-output`：

| 文件/目录 | 说明 |
| --- | --- |
| `run.bat` | Windows 双击启动脚本 |
| `run.ps1` | PowerShell 启动脚本 |
| `Canteen-Simulation.jar` | 可选 JAR 运行包 |
| `lib` | 第三方依赖 |
| `docs` | 部署说明、使用手册、流程图和缺失项检查表 |

## 生成免配置运行包

如果开发者需要重新生成免配置发布包，在有 JDK 的开发机上执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-release.ps1 -Clean
```

生成结果位于：

```text
release-output\Canteen-Simulation-Portable-Windows.zip
```

该 zip 可以上传到 GitHub Release，供普通用户下载运行。

## 项目信息

- 源码启动入口：`backend.Main`
- 实际图形界面入口：`frontend.MainDashboard`
- UI 技术：Swing
- 依赖管理：普通 Java 工程，无 Maven/Gradle
- 数据库：当前不需要数据库

