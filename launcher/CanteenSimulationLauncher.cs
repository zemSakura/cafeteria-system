using System;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Windows.Forms;

namespace CanteenSimulationLauncher
{
    internal static class Program
    {
        [STAThread]
        private static void Main()
        {
            string appDir = AppDomain.CurrentDomain.BaseDirectory;
            string runScript = Path.Combine(appDir, "run.ps1");

            if (!File.Exists(runScript))
            {
                MessageBox.Show(
                    "未找到 run.ps1。请确认启动器位于项目根目录，并且项目文件完整。",
                    "Canteen-Simulation",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
                return;
            }

            string command = BuildPowerShellCommand(runScript);

            try
            {
                ProcessStartInfo startInfo = new ProcessStartInfo
                {
                    FileName = "powershell.exe",
                    Arguments = "-NoProfile -ExecutionPolicy Bypass -Command " + Quote(command),
                    WorkingDirectory = appDir,
                    UseShellExecute = true,
                    WindowStyle = ProcessWindowStyle.Normal
                };

                Process.Start(startInfo);
            }
            catch (Exception ex)
            {
                MessageBox.Show(
                    "启动 PowerShell 失败：" + ex.Message,
                    "Canteen-Simulation",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
            }
        }

        private static string BuildPowerShellCommand(string runScript)
        {
            string escapedScript = runScript.Replace("'", "''");
            StringBuilder builder = new StringBuilder();
            builder.Append("try { ");
            builder.Append("& '").Append(escapedScript).Append("'; ");
            builder.Append("if ($LASTEXITCODE -ne $null -and $LASTEXITCODE -ne 0) { ");
            builder.Append("Write-Host ''; Read-Host '运行失败，按 Enter 退出' ");
            builder.Append("} ");
            builder.Append("} catch { ");
            builder.Append("Write-Host ''; Write-Host $_; Write-Host ''; Read-Host '运行失败，按 Enter 退出' ");
            builder.Append("}");
            return builder.ToString();
        }

        private static string Quote(string value)
        {
            return "\"" + value.Replace("\"", "\\\"") + "\"";
        }
    }
}
