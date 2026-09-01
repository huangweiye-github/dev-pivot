package com.hwy.devpivot.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ReadTool implements DevPivotTool {

private static final int MAX_LINES = 2000;
    private static final String[] IMG_EXT = {".png",".jpg",".jpeg",".gif",".bmp",".webp",".svg",".ico"};

    @Tool(name = "Read", value = "读取本地文件系统中的文件内容，以cat -n格式返回文件内容，包含行号。支持offset和limit参数进行分段读取，也支持PDF页码范围。")
    public String read(
            @P("文件绝对路径，必须为绝对路径") String file_path,
            @P("起始行号（从1开始）") Integer offset,
            @P("读取行数上限，默认2000") Integer limit,
            @P("PDF文件的页面范围，如\"1-5\"") String pages) {

        if (file_path == null || file_path.isBlank()) return "Error: file_path 为必填参数";
        Path path = Paths.get(file_path);
        if (!path.isAbsolute()) return "Error: 文件路径必须是绝对路径";
        if (!Files.exists(path)) return "Error: 文件不存在: " + file_path;
        if (Files.isDirectory(path)) return "Error: 路径指向目录而非文件: " + file_path;

        String fn = path.getFileName().toString().toLowerCase();
        for (String ext : IMG_EXT) {
            if (fn.endsWith(ext)) {
                long sz = 0; try { sz = Files.size(path); } catch (IOException ignored) {}
                return "[图片文件] " + file_path + " (" + fmtSize(sz) + ")";
            }
        }
        return fn.endsWith(".pdf") ? readPdf(path, pages) : readText(path, offset, limit);
    }

    private String readText(Path path, Integer offset, Integer limit) {
        try {
            List<String> all = Files.readAllLines(path);
            int total = all.size();
            int start = (offset != null && offset > 0) ? Math.min(offset - 1, total) : 0;
            int end = (limit != null && limit > 0) ? Math.min(start + limit, total) : Math.min(start + MAX_LINES, total);
            List<String> sel = all.subList(start, end);
            StringBuilder sb = new StringBuilder();
            sb.append("File: ").append(path).append("\nLines: ").append(total);
            if (start > 0 || end < total) sb.append(", showing ").append(start+1).append("-").append(end);
            sb.append("\n").append("─".repeat(60)).append("\n");
            for (int i = 0; i < sel.size(); i++)
                sb.append(String.format("%6d\t%s%n", start + i + 1, sel.get(i)));
            if (end < total) sb.append("\n... (使用 offset=").append(end+1).append(" 读取后续内容)");
            return sb.toString();
        } catch (IOException e) { return "Error: 读取失败: " + e.getMessage(); }
    }

    private String readPdf(Path path, String pages) {
        StringBuilder sb = new StringBuilder();
        sb.append("[PDF文件] ").append(path).append("\n");
        try { sb.append("大小: ").append(fmtSize(Files.size(path))).append("\n"); } catch (IOException ignored) {}
        if (pages != null && !pages.isBlank()) sb.append("请求页面: ").append(pages).append("\n");
        sb.append("提示: 完整PDF提取需要PDFBox等第三方库\n");
        try { String raw = Files.readString(path); sb.append(raw.length() > 4000 ? raw.substring(0,4000)+"\n...(截断)" : raw); }
        catch (IOException e) { sb.append("[无法读取: ").append(e.getMessage()).append("]"); }
        return sb.toString();
    }

    private static String fmtSize(long b) {
        if (b < 1024) return b+" B";
        if (b < 1024*1024) return String.format("%.1f KB", b/1024.0);
        if (b < 1024*1024*1024) return String.format("%.1f MB", b/(1024.0*1024));
        return String.format("%.2f GB", b/(1024.0*1024*1024));
    }
}
