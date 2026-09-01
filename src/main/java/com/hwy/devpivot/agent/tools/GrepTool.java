package com.hwy.devpivot.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.*;

public class GrepTool implements DevPivotTool {

private static final int DEFAULT_HEAD = 250;
    private static final int MAX_RESULTS = 1000;
    private static final int MAX_DEPTH = 30;
    private static final Set<String> IGNORES = Set.of(
            "node_modules",".git",".svn",".hg","target","build","dist","out",
            "__pycache__",".idea",".vscode",".vs","vendor","bower_components");

    private static Map<String,List<String>> typeExts() {
        Map<String,List<String>> m = new HashMap<>();
        m.put("java",List.of(".java")); m.put("js",List.of(".js",".jsx",".mjs",".cjs"));
        m.put("ts",List.of(".ts",".tsx")); m.put("py",List.of(".py",".pyi",".pyx"));
        m.put("rust",List.of(".rs")); m.put("go",List.of(".go"));
        m.put("xml",List.of(".xml",".xsl",".xsd")); m.put("json",List.of(".json"));
        m.put("yaml",List.of(".yml",".yaml")); m.put("md",List.of(".md",".mdx"));
        m.put("html",List.of(".html",".htm")); m.put("css",List.of(".css",".scss",".less"));
        m.put("sql",List.of(".sql")); m.put("sh",List.of(".sh",".bash",".zsh"));
        m.put("properties",List.of(".properties")); m.put("txt",List.of(".txt"));
        return m;
    }

    @Tool(name = "Grep", value = "基于正则表达式的内容搜索。必填: pattern。可选: path/glob/output_mode/-A/-B/-C/-n/multiline/head_limit/type。")
    public String grep(@P("搜索参数") GrepArgs args) {
        if (args == null || args.pattern == null || args.pattern.isBlank()) return "Error: pattern 为必填";

        int flags = 0;
        if (args.caseInsensitive != null && args.caseInsensitive) flags |= Pattern.CASE_INSENSITIVE;
        if (args.multiline != null && args.multiline) flags |= Pattern.DOTALL | Pattern.MULTILINE;
        Pattern regex; try { regex = Pattern.compile(args.pattern, flags); } catch (PatternSyntaxException e) { return "Error: 正则语法错误: " + e.getMessage(); }

        Path baseDir = (args.path != null && !args.path.isBlank()) ? Paths.get(args.path) : Paths.get("").toAbsolutePath();
        if (!Files.isDirectory(baseDir)) return "Error: 目录不存在: " + baseDir;

        String mode = args.outputMode != null ? args.outputMode : "files_with_matches";
        int before = Math.max(args.before != null ? args.before : 0, args.context != null ? args.context : 0);
        int after = Math.max(args.after != null ? args.after : 0, args.context != null ? args.context : 0);
        int headL = args.headLimit != null ? args.headLimit : DEFAULT_HEAD;
        if (headL <= 0) headL = Integer.MAX_VALUE;
        var typeExts = typeExts();

        try {
            List<GrepMatch> all = new ArrayList<>();
            Set<Path> matchedFiles = new LinkedHashSet<>();

            Files.walkFileTree(baseDir, java.util.Set.of(), MAX_DEPTH, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                    String n = d.getFileName().toString();
                    return (n.startsWith(".") || IGNORES.contains(n)) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    if (all.size() >= MAX_RESULTS) return FileVisitResult.TERMINATE;
                    String fn = f.getFileName().toString();
                    if (args.glob != null && !args.glob.isBlank() && !FileSystems.getDefault().getPathMatcher("glob:"+args.glob).matches(f.getFileName()))
                        return FileVisitResult.CONTINUE;
                    if (args.type != null && !args.type.isBlank()) {
                        List<String> exts = typeExts.get(args.type);
                        if (exts != null && exts.stream().noneMatch(fn::endsWith)) return FileVisitResult.CONTINUE;
                    }
                    if (a.size() > 1024*1024) return FileVisitResult.CONTINUE;
                    try {
                        List<String> lines = Files.readAllLines(f);
                        for (int i = 0; i < lines.size(); i++)
                            if (regex.matcher(lines.get(i)).find()) {
                                all.add(new GrepMatch(f, i, lines)); matchedFiles.add(f);
                                if (all.size() >= MAX_RESULTS) return FileVisitResult.TERMINATE;
                            }
                    } catch (IOException ignored) {}
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path f, IOException e) { return FileVisitResult.SKIP_SUBTREE; }
            });

            StringBuilder sb = new StringBuilder();
            if ("count".equals(mode)) {
                sb.append("Pattern: /").append(args.pattern).append("/\nMatches: ").append(all.size()).append(" (").append(matchedFiles.size()).append(" files)\n").append("─".repeat(40)).append("\n");
                Map<Path,Long> cnts = new LinkedHashMap<>();
                for (GrepMatch m : all) cnts.merge(m.file, 1L, Long::sum);
                int shown = 0;
                for (var e : cnts.entrySet()) {
                    if (shown++ >= headL) { sb.append("...(head_limit)\n"); break; }
                    sb.append("  ").append(baseDir.relativize(e.getKey())).append(": ").append(e.getValue()).append("\n");
                }
            } else if ("content".equals(mode)) {
                sb.append("Pattern: /").append(args.pattern).append("/\nMode: content\n").append("─".repeat(60)).append("\n");
                int shown = 0; Set<String> shownCtx = new LinkedHashSet<>();
                for (GrepMatch m : all) {
                    if (shown >= headL) { sb.append("...(head_limit)\n"); break; }
                    int cs = Math.max(0, m.li-before), ce = Math.min(m.all.size(), m.li+after+1);
                    String key = m.file+":"+cs+"-"+ce;
                    if (shownCtx.contains(key)) continue; shownCtx.add(key);
                    sb.append("── ").append(baseDir.relativize(m.file)).append(" ──\n");
                    for (int i = cs; i < ce; i++)
                        sb.append(String.format("%s %6d\t%s%n", i==m.li?"▶":" ", i+1, m.all.get(i)));
                    shown++;
                }
            } else {
                sb.append("Pattern: /").append(args.pattern).append("/\nMatching files: ").append(matchedFiles.size()).append("\n").append("─".repeat(60)).append("\n");
                int shown = 0;
                for (Path f : matchedFiles) {
                    if (shown++ >= headL) { sb.append("...(head_limit)\n"); break; }
                    long cnt = all.stream().filter(m->m.file.equals(f)).count();
                    sb.append("  ").append(baseDir.relativize(f)).append("  (").append(cnt).append(" matches)\n");
                }
            }
            return sb.toString();
        } catch (IOException e) { return "Error: " + e.getMessage(); }
    }

    private record GrepMatch(Path file, int li, List<String> all) {}

    // ── POJO ──────────────────────────────────────────

    public static class GrepArgs {
        @Description("正则表达式搜索模式，必填")
        public String pattern;
        @Description("搜索目录路径，默认为当前工作目录")
        public String path;
        @Description("文件名glob过滤，如 *.java")
        public String glob;
        @Description("输出模式: files_with_matches/content/count，默认 files_with_matches")
        public String outputMode;
        @Description("显示匹配行之前N行")
        public Integer before;
        @Description("显示匹配行之后N行")
        public Integer after;
        @Description("显示匹配行前后各N行，与before/after叠加")
        public Integer context;
        @Description("是否忽略大小写")
        public Boolean caseInsensitive;
        @Description("是否显示行号，默认true")
        public Boolean showLineNum;
        @Description("是否多行模式(跨行匹配)")
        public Boolean multiline;
        @Description("限制输出行数，默认250")
        public Integer headLimit;
        @Description("按文件类型过滤，如 java/js/py/go")
        public String type;
    }
}
