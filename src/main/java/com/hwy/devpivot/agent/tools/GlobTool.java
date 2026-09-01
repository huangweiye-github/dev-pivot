package com.hwy.devpivot.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class GlobTool implements DevPivotTool {

private static final int MAX_RESULTS = 500;
    private static final int MAX_DEPTH = 20;
    private static final List<String> IGNORES = List.of(
            "node_modules", ".git", ".svn", ".hg", "target", "build", "dist", "out",
            "__pycache__", ".idea", ".vscode", ".vs", "vendor", "bower_components");

    @Tool(name = "Glob", value = "快速文件模式匹配搜索。**匹配多级目录，*匹配单层文件名。结果按修改时间降序排列。")
    public String glob(
            @P("glob模式，如 **/*.js") String pattern,
            @P("搜索基准目录，默认当前目录") String path) {

        if (pattern == null || pattern.isBlank()) return "Error: pattern 为必填参数";
        Path baseDir = (path != null && !path.isBlank()) ? Paths.get(path).toAbsolutePath().normalize() : Paths.get("").toAbsolutePath();
        if (!Files.isDirectory(baseDir)) return "Error: 目录不存在: " + baseDir;

        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            // Windows 上 **/ 不匹配零级目录，补一个去掉 **/ 的 matcher 兜底
            PathMatcher flatMatcher = pattern.startsWith("**/") ? FileSystems.getDefault().getPathMatcher("glob:" + pattern.substring(3)) : null;
            List<PathMatch> matches = new ArrayList<>();

            Files.walkFileTree(baseDir, java.util.Set.of(), MAX_DEPTH, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                    String n = d.getFileName().toString();
                    return (n.startsWith(".") || IGNORES.contains(n)) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    if (matches.size() >= MAX_RESULTS) return FileVisitResult.TERMINATE;
                    Path rel = baseDir.relativize(f);
                    Path name = f.getFileName();
                    if (matcher.matches(rel) || matcher.matches(name)
                            || (flatMatcher != null && (flatMatcher.matches(rel) || flatMatcher.matches(name))))
                        matches.add(new PathMatch(f, rel, a));
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path f, IOException e) { return FileVisitResult.SKIP_SUBTREE; }
            });

            matches.sort(Comparator.comparingLong((PathMatch m) -> m.attrs.lastModifiedTime().toMillis()).reversed());

            StringBuilder sb = new StringBuilder();
            sb.append("Glob: ").append(pattern).append("\nPath: ").append(baseDir).append("\nMatches: ").append(matches.size());
            if (matches.size() >= MAX_RESULTS) sb.append(" (max ").append(MAX_RESULTS).append(")");
            sb.append("\n").append("─".repeat(60)).append("\n");
            if (matches.isEmpty()) sb.append("(无匹配)\n");
            else for (PathMatch m : matches) sb.append("  ").append(m.rel.toString().replace('\\','/')).append("  (").append(fmtSize(m.attrs.size())).append(")\n");
            return sb.toString();
        } catch (IOException e) { return "Error: " + e.getMessage(); }
    }

    private static String fmtSize(long b) {
        if (b < 1024) return b+" B";
        if (b < 1024*1024) return String.format("%.1f KB", b/1024.0);
        if (b < 1024*1024*1024) return String.format("%.1f MB", b/(1024.0*1024));
        return String.format("%.2f GB", b/(1024.0*1024*1024));
    }

    private record PathMatch(Path abs, Path rel, BasicFileAttributes attrs) {}
}
