package com.hwy.devpivot.env;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 环境变量读取器，每个变量一个方法，获取成功后缓存，最后通过 {@link #getAllVariables()}
 * 返回所有变量的 map，供 MarkdownTemplate 占位符替换使用。
 *
 * <pre>{@code
 * EnvironmentReader reader = new EnvironmentReader();
 * String prompt = MarkdownTemplate.replaceFromResource(
 *         "prompt/coding-agent.md",
 *         reader.getAllVariables());
 * }</pre>
 */
public class EnvironmentReader {

    public static final String KEY_CURRENT_WORK_DIR = "currentWorkDir";
    public static final String KEY_IS_GIT_REPOSITORY = "isGitRepository";
    public static final String KEY_PLATFORM = "platform";
    public static final String KEY_SHELL_PROVIDER = "shellProvider";
    public static final String KEY_OS_VERSION = "osVersion";
    public static final String KEY_MODEL_NAME = "modelName";
    public static final String KEY_CURRENT_GIT_BRANCH = "currentGitBranch";
    public static final String KEY_MASTER_BRANCH = "masterBranch";
    public static final String KEY_GIT_USERNAME = "gitUsername";

    /** 缓存，key 为占位符名，value 为获取到的值 */
    private static final Map<String, String> cache = new HashMap<>();

    // ── 每个变量一个方法 ──────────────────────────────────

    /** 获取当前工作目录，推断项目根目录 */
    public static String getCurrentWorkDir() {
        return cache.computeIfAbsent(KEY_CURRENT_WORK_DIR, k -> inferWorkDir());
    }

    /** 检测是否为 Git 仓库（向上查找 .git 目录） */
    public static String getIsGitRepository() {
        return cache.computeIfAbsent(KEY_IS_GIT_REPOSITORY, k -> String.valueOf(isGitRepo()));
    }

    /** 获取平台标识：win32 / darwin / linux */
    public static String getPlatform() {
        return cache.computeIfAbsent(KEY_PLATFORM, k -> detectPlatform());
    }

    /** 获取 Shell 类型 */
    public static String getShellProvider() {
        return cache.computeIfAbsent(KEY_SHELL_PROVIDER, k -> detectShell());
    }

    /** 获取操作系统版本 */
    public static String getOsVersion() {
        return cache.computeIfAbsent(KEY_OS_VERSION, k -> System.getProperty("os.name") + " " + System.getProperty("os.version"));
    }

    /** 获取当前模型名称 */
    public static String getModelName() {
        return cache.computeIfAbsent(KEY_MODEL_NAME, k -> System.getenv().getOrDefault("MODEL_NAME", "unknown"));
    }

    /** 获取当前 Git 分支名称，非 Git 仓库时返回 "N/A" */
    public static String getCurrentGitBranch() {
        return cache.computeIfAbsent(KEY_CURRENT_GIT_BRANCH, k -> {
            if (!isGitRepo()) return "N/A";
            return detectCurrentGitBranch();
        });
    }

    /** 获取主分支名称（main 或 master），非 Git 仓库时返回 "N/A" */
    public static String getMasterBranchName() {
        return cache.computeIfAbsent(KEY_MASTER_BRANCH, k -> {
            if (!isGitRepo()) return "N/A";
            return detectMasterBranch();
        });
    }

    /** 获取 Git 用户名，非 Git 仓库时返回 "N/A" */
    public static String getGitUsername() {
        return cache.computeIfAbsent(KEY_GIT_USERNAME, k -> {
            if (!isGitRepo()) return "N/A";
            String name = execGit("config", "user.name");
            return name.isEmpty() ? "N/A" : name;
        });
    }

    // ── 汇总返回 ──────────────────────────────────────────

    /**
     * 触发所有变量的获取并缓存，返回包含全部变量的 map。
     * 其中 key 为占位符名（如 "currentWorkDir"），value 为对应值。
     */
    public static Map<String, String> getAllVariables() {
        // 确保所有变量都已加载到缓存
        getCurrentWorkDir();
        getIsGitRepository();
        getPlatform();
        getShellProvider();
        getOsVersion();
        getModelName();
        getCurrentGitBranch();
        getMasterBranchName();
        getGitUsername();
        return new HashMap<>(cache);
    }

    // ── 内部实现 ──────────────────────────────────────────

    /** 获取程序当前启动目录的绝对路径 */
    private static String inferWorkDir() {
        String currentWorkDir = System.getenv("currentWorkDir");
        return Paths.get(
                    currentWorkDir != null ? currentWorkDir : System.getProperty("user.dir"))
                .toAbsolutePath().normalize().toString();
    }

    /** 判断 inferWorkDir() 对应目录是否为 Git 仓库 */
    private static boolean isGitRepo() {
        return Files.exists(Paths.get(inferWorkDir()).resolve(".git"));
    }

    /** 执行 git 命令并返回修剪后的输出，失败时返回空字符串 */
    private static String execGit(String... args) {
        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = "git";
            System.arraycopy(args, 0, cmd, 1, args.length);
            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            return p.waitFor() == 0 ? out : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 通过 git rev-parse 获取当前分支名 */
    private static String detectCurrentGitBranch() {
        String branch = execGit("rev-parse", "--abbrev-ref", "HEAD");
        return branch.isEmpty() ? "N/A" : branch;
    }

    /** 探测主分支名称，优先 main，其次 master */
    private static String detectMasterBranch() {
        if (!execGit("rev-parse", "--verify", "main").isEmpty()) return "main";
        if (!execGit("rev-parse", "--verify", "master").isEmpty()) return "master";
        return "main"; // 兜底
    }

    /** 将 os.name 映射为简短平台标识 */
    private static String detectPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "win32";
        if (os.contains("mac") || os.contains("darwin")) return "darwin";
        return "linux";
    }

    /** 按优先级检测可用的 Shell（检查可执行程序是否存在） */
    private static String detectShell() {
        // Unix Shell 优先：bash > zsh > sh
        if (isExecutableAvailable("bash", "-c", "exit 0")) return "bash";
        if (isExecutableAvailable("zsh", "-c", "exit 0")) return "zsh";
        if (isExecutableAvailable("sh", "-c", "exit 0")) return "sh";
        // Windows Shell
        if (isExecutableAvailable("powershell", "-Command", "exit 0")) return "powershell";
        if (isExecutableAvailable("cmd", "/c", "exit 0")) return "cmd";
        // 兜底
        return detectPlatform().equals("win32") ? "cmd" : "sh";
    }

    /** 尝试启动可执行程序并等待退出，能启动即认为可用 */
    private static boolean isExecutableAvailable(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.waitFor();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
