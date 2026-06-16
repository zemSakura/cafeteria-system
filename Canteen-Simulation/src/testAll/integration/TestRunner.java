package testAll.integration;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 联调测试总运行器。
 * 依次运行接口联调测试和系统联调测试，结果同时输出到控制台和文件。
 */
public class TestRunner {

    public static void main(String[] args) {
        String outputDir = System.getProperty("user.dir");
        Path outputFile = Paths.get(outputDir, "integration_test_result.txt");

        System.out.println("============================================");
        System.out.println("  食堂仿真系统 - 完整联调测试运行器");
        System.out.println("============================================");
        System.out.println("  结果文件: " + outputFile.toAbsolutePath());
        System.out.println("============================================\n");

        try (PrintStream fileOut = new PrintStream(new FileOutputStream(outputFile.toFile()), true, "UTF-8")) {
            // 同时输出到控制台和文件
            TeePrintStream tee = new TeePrintStream(System.out, fileOut);
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            System.setOut(tee);
            System.setErr(tee);

            try {
                System.out.println("测试开始时间: " + java.time.LocalDateTime.now());
                System.out.println("JDK版本: " + System.getProperty("java.version"));
                System.out.println("操作系统: " + System.getProperty("os.name"));
                System.out.println();

                // ===================== 第一阶段：接口联调测试 =====================
                System.out.println("##############################################");
                System.out.println("#  第一阶段：接口联调测试");
                System.out.println("##############################################\n");

                Thread ifTest = new Thread(() -> {
                    try {
                        IntegrationTest.main(new String[0]);
                    } catch (Exception e) {
                        System.out.println("接口联调测试运行异常: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
                ifTest.start();
                ifTest.join(300000); // 5分钟超时
                if (ifTest.isAlive()) {
                    System.out.println("警告: 接口联调测试超时，继续下一阶段");
                    ifTest.interrupt();
                }

                System.out.println("\n\n");

                // ===================== 第二阶段：系统联调测试 =====================
                System.out.println("##############################################");
                System.out.println("#  第二阶段：系统联调测试");
                System.out.println("##############################################\n");

                Thread sysTest = new Thread(() -> {
                    try {
                        SystemIntegrationTest.main(new String[0]);
                    } catch (Exception e) {
                        System.out.println("系统联调测试运行异常: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
                sysTest.start();
                sysTest.join(600000); // 10分钟超时
                if (sysTest.isAlive()) {
                    System.out.println("警告: 系统联调测试超时");
                    sysTest.interrupt();
                }

                System.out.println("\n\n");
                System.out.println("测试结束时间: " + java.time.LocalDateTime.now());

            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        } catch (Exception e) {
            System.err.println("无法创建输出文件: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 将输出同时写入控制台和文件的 PrintStream 包装器。
     */
    private static class TeePrintStream extends PrintStream {
        private final PrintStream original;
        private final PrintStream file;

        TeePrintStream(PrintStream original, PrintStream file) {
            super(original);
            this.original = original;
            this.file = file;
        }

        @Override
        public void write(int b) {
            original.write(b);
            file.write(b);
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            original.write(buf, off, len);
            file.write(buf, off, len);
        }

        @Override
        public void flush() {
            original.flush();
            file.flush();
        }

        @Override
        public void close() {
            file.close();
        }
    }
}
