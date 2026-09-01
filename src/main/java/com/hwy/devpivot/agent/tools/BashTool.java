package com.hwy.devpivot.agent.tools;

import com.hwy.devpivot.env.EnvironmentReader;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class BashTool implements DevPivotTool {

private static final long DEF_TIMEOUT = 120_000L;
    private static final long MAX_TIMEOUT = 600_000L;
    private static final int MAX_OUT = 100_000;
    private static final Map<String, BgTask> TASKS = new ConcurrentHashMap<>();

    @Tool(name = "Bash", value = "执行bash命令并返回输出。必填: command。可选: description/timeout/run_in_background/dangerouslyDisableSandbox。后台返回task_id。")
    public String bash(
            @P("要执行的shell命令") String command,
            @P("命令描述") String description,
            @P("超时毫秒数，默认120000") Integer timeout,
            @P("是否后台运行") Boolean run_in_background,
            @P("是否禁用沙箱") Boolean dangerouslyDisableSandbox) {

        if (command == null || command.isBlank()) return "Error: command 为必填";
        long toMs = (timeout != null && timeout > 0) ? Math.min(timeout, MAX_TIMEOUT) : DEF_TIMEOUT;
        boolean bg = run_in_background != null && run_in_background;
        boolean noSb = dangerouslyDisableSandbox != null && dangerouslyDisableSandbox;

        if (bg) return execBg(command, description, toMs, noSb);
        return execFg(command, description, toMs, noSb);
    }

    private String execFg(String cmd, String desc, long toMs, boolean noSb) {
        try {
            Process p = buildProc(cmd).start();
            StringBuilder out = new StringBuilder(), err = new StringBuilder();
            Thread ot = new Thread(() -> read(utf8Reader(p, true), out, noSb));
            Thread et = new Thread(() -> read(utf8Reader(p, false), err, noSb));
            ot.start(); et.start();
            boolean ok = p.waitFor(toMs, TimeUnit.MILLISECONDS);
            long ec;
            if (ok) { ot.join(5000); et.join(5000); ec = p.exitValue(); }
            else { p.destroyForcibly(); ot.interrupt(); et.interrupt(); ec = -1; }
            return fmt(desc, cmd, ec, ok, out.toString(), err.toString(), toMs);
        } catch (IOException e) { return "Error: " + e.getMessage(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return "Error: 被中断"; }
    }

    private String execBg(String cmd, String desc, long toMs, boolean noSb) {
        String tid = UUID.randomUUID().toString().substring(0, 8);
        BgTask t = new BgTask(); t.cmd = cmd; t.status = "running"; t.start = System.currentTimeMillis();
        Thread w = new Thread(() -> {
            try {
                Process p = buildProc(cmd).start();
                StringBuilder out = new StringBuilder(), err = new StringBuilder();
                Thread ot = new Thread(() -> read(utf8Reader(p, true), out, noSb));
                Thread et = new Thread(() -> read(utf8Reader(p, false), err, noSb));
                ot.start(); et.start();
                boolean ok = p.waitFor(toMs, TimeUnit.MILLISECONDS);
                if (ok) { ot.join(5000); et.join(5000); t.ec = p.exitValue(); t.status = t.ec==0?"completed":"failed"; }
                else { p.destroyForcibly(); ot.interrupt(); et.interrupt(); t.ec = -1; t.status = "timeout"; }
                t.out = out.toString(); t.err = err.toString();
            } catch (Exception e) { t.status = "error"; t.errMsg = e.getMessage(); }
            t.dur = System.currentTimeMillis() - t.start;
        }, "bg-"+tid);
        t.thread = w; TASKS.put(tid, t); w.start();
        if (TASKS.size() > 50) TASKS.entrySet().removeIf(e -> !"running".equals(e.getValue().status));
        return "✅ 后台任务 " + tid + "\n   命令: " + cmd + "\n   状态: running";
    }

    private ProcessBuilder buildProc(String cmd) {
        String shell = EnvironmentReader.getShellProvider();
        ProcessBuilder pb = switch (shell) {
            case "powershell" -> {
                String wrapped = "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8;" + cmd;
                yield new ProcessBuilder("powershell", "-NoProfile", "-Command", wrapped);
            }
            case "cmd" -> {
                String wrapped = "chcp 65001 > nul && " + cmd;
                yield new ProcessBuilder("cmd", "/c", wrapped);
            }
            default -> new ProcessBuilder(shell, "-c", cmd);
        };
        // 为 bash/zsh 设置 UTF-8 locale 环境变量
        if (!"cmd".equals(shell) && !"powershell".equals(shell)) {
            Map<String, String> env = pb.environment();
            env.putIfAbsent("LANG", "en_US.UTF-8");
            env.putIfAbsent("LC_ALL", "en_US.UTF-8");
        }
        return pb;
    }

    private BufferedReader utf8Reader(Process p, boolean stdOut) {
        return new BufferedReader(new InputStreamReader(
                stdOut ? p.getInputStream() : p.getErrorStream(), StandardCharsets.UTF_8));
    }

    private void read(BufferedReader r, StringBuilder sb, boolean noSb) {
        int limit = noSb ? Integer.MAX_VALUE : MAX_OUT;
        try (r) { String l; int c=0; while ((l=r.readLine())!=null) { int nl=c+l.length()+1; if (nl>limit) { sb.append(l,0,Math.max(0,limit-c)).append("\n...[截断]\n"); break; } sb.append(l).append("\n"); c=nl; } }
        catch (IOException ignored) {}
    }

    private String fmt(String desc, String cmd, long ec, boolean ok, String out, String err, long toMs) {
        StringBuilder sb = new StringBuilder();
        if (desc != null && !desc.isBlank()) sb.append("Command: ").append(desc).append("\n");
        sb.append("$ ").append(cmd).append("\n");
        if (!ok) sb.append("\n⏱ 超时(").append(toMs).append("ms)\n");
        if (!out.isEmpty()) sb.append("\n── stdout ──\n").append(out);
        if (!err.isEmpty()) sb.append("\n── stderr ──\n").append(err);
        sb.append("\nExit code: ").append(ec);
        if (ok && out.isEmpty() && err.isEmpty()) sb.append(" (无输出)");
        return sb.toString();
    }

    public static String taskStatus(String tid) {
        BgTask t = TASKS.get(tid);
        if (t == null) return "Error: task_id='" + tid + "' 不存在";
        StringBuilder sb = new StringBuilder();
        sb.append("── Task ").append(tid).append(" ──\n");
        sb.append("  状态: ").append(t.status).append("\n  命令: ").append(t.cmd).append("\n  耗时: ").append(t.dur).append("ms\n");
        if ("running".equals(t.status)) sb.append("  运行中: ").append(System.currentTimeMillis()-t.start).append("ms\n");
        else { sb.append("  Exit: ").append(t.ec).append("\n"); if (t.out!=null&&!t.out.isEmpty()) sb.append("\n── stdout ──\n").append(t.out); if (t.err!=null&&!t.err.isEmpty()) sb.append("\n── stderr ──\n").append(t.err); if (t.errMsg!=null) sb.append("\n  Error: ").append(t.errMsg).append("\n"); }
        return sb.toString();
    }

    public static String listTasks() {
        if (TASKS.isEmpty()) return "无后台任务";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  %-10s %-12s %-8s %s%n","TASK_ID","STATUS","EXIT","COMMAND"));
        sb.append("─".repeat(70)).append("\n");
        for (var e : TASKS.entrySet()) { BgTask t = e.getValue(); sb.append(String.format("  %-10s %-12s %-8d %s%n", e.getKey(), t.status, t.ec, t.cmd.length()>40?t.cmd.substring(0,40)+"...":t.cmd)); }
        return sb.toString();
    }

    private static class BgTask { String cmd, status, out, err, errMsg; long start, dur; int ec; Thread thread; }
}
