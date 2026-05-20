package dev.msf.friends.util;

import org.slf4j.Logger;

import java.awt.Desktop;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 启动时检测当前进程是否由 Plain Craft Launcher 启动。
 *
 * <p>检测流程：
 * <ol>
 *   <li>确认运行在 Windows 系统上；</li>
 *   <li>获取当前 JVM 进程的父进程；</li>
 *   <li>通过 PowerShell 读取父进程可执行文件的 FileVersionInfo.FileDescription；</li>
 *   <li>若描述匹配 "Plain Craft Launcher 启动器"，则自动打开指定页面。</li>
 * </ol>
 */
public final class PclDetector {

    private static final Logger LOGGER = Logging.get();

    private static final String TARGET_DESCRIPTION = "Plain Craft Launcher 启动器";
    private static final String TARGET_URL         = "https://2pcl.912778.xyz";

    private PclDetector() {}

    /**
     * 在启动时（守护线程中）调用此方法。
     * 检测到 PCL 时自动打开页面，否则静默返回。
     */
    public static void checkAndOpenIfPCL() {
        // 0. 若 ~/.yespcl 文件存在则跳过检测
        Path optOut = Path.of(System.getProperty("user.home"), ".yespcl");
        if (java.nio.file.Files.exists(optOut)) {
            LOGGER.debug("[pcl-detect] 检测到 ~/.yespcl 文件，跳过检测");
            return;
        }

        // 1. 仅在 Windows 上执行
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            LOGGER.debug("[pcl-detect] 非 Windows 系统，跳过检测");
            return;
        }

        try {
            // 2. 获取父进程句柄
            ProcessHandle parent = ProcessHandle.current().parent().orElse(null);
            if (parent == null) {
                LOGGER.debug("[pcl-detect] 无父进程，跳过检测");
                return;
            }

            // 3. 获取父进程可执行文件路径
            String exePath = parent.info().command().orElse(null);
            if (exePath == null || exePath.isBlank()) {
                LOGGER.debug("[pcl-detect] 无法获取父进程路径，跳过检测");
                return;
            }
            LOGGER.debug("[pcl-detect] 父进程路径: {}", exePath);

            // 4. 读取可执行文件的 FileDescription
            String description = readFileDescription(exePath);
            LOGGER.debug("[pcl-detect] 父进程 FileDescription: {}", description);

            // 5. 匹配后打开页面
            if (TARGET_DESCRIPTION.equals(description)) {
                LOGGER.info("[pcl-detect] 检测到 PCL 启动器，正在打开 {}", TARGET_URL);
                openUrl(TARGET_URL);
            }

        } catch (Throwable t) {
            LOGGER.debug("[pcl-detect] 检测异常: {}", t.getMessage());
        }
    }

    /**
     * 通过 PowerShell 读取 PE 可执行文件的 FileVersionInfo.FileDescription。
     * 出错时返回空字符串。
     */
    private static String readFileDescription(String exePath) {
        try {
            // 对 PowerShell 单引号字符串中的单引号进行转义
            String safePath = exePath.replace("'", "''");
            String psCommand =
                    "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8;" +
                    "(Get-Item -LiteralPath '" + safePath + "').VersionInfo.FileDescription";

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", psCommand);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            byte[] output = process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                LOGGER.debug("[pcl-detect] PowerShell 查询超时");
            }

            return new String(output, StandardCharsets.UTF_8).trim();

        } catch (Exception e) {
            LOGGER.debug("[pcl-detect] readFileDescription 失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 使用系统默认浏览器打开指定 URL。
     */
    private static void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI(url));
                    return;
                }
            }
            // 备选方案：调用 Windows rundll32
            Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
        } catch (Exception e) {
            LOGGER.warn("[pcl-detect] 打开 URL 失败 {}: {}", url, e.getMessage());
        }
    }
}
