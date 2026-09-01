package com.hwy.devpivot.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

public class EditTool implements DevPivotTool {

@Tool(name = "Edit", value = "在文件中执行精确的字符串替换。old_string必须与文件内容完全一致（含空白/缩进）且唯一。replace_all=true替换全部匹配项。")
    public String edit(
            @P("文件绝对路径") String file_path,
            @P("要被替换的文本，必须完全一致且唯一") String old_string,
            @P("替换后的文本") String new_string,
            @P("是否替换全部匹配项") Boolean replace_all) {

        if (file_path == null || file_path.isBlank()) return "Error: file_path 为必填参数";
        if (old_string == null || old_string.isEmpty()) return "Error: old_string 为必填参数";
        if (new_string == null) return "Error: new_string 为必填参数";
        if (old_string.equals(new_string)) return "Error: old_string 和 new_string 必须不同";

        boolean replaceAll = replace_all != null && replace_all;
        Path path = Paths.get(file_path);
        if (!path.isAbsolute()) return "Error: 必须是绝对路径";
        if (!Files.exists(path)) return "Error: 文件不存在: " + file_path;
        if (Files.isDirectory(path)) return "Error: 路径指向目录";

        try {
            String content = Files.readString(path);
            int idx = content.indexOf(old_string);
            if (idx == -1) {
                StringBuilder d = new StringBuilder();
                d.append("Error: 未找到 old_string。\n文件: ").append(file_path).append("\n");
                String fl = old_string.split("\n",2)[0].strip();
                String[] cls = content.split("\n");
                int best = -1, bestD = Integer.MAX_VALUE;
                for (int i = 0; i < cls.length; i++) {
                    String s = cls[i].strip();
                    if (!s.isEmpty() && s.length() > 3) {
                        int dist = lev(s.substring(0,Math.min(s.length(),fl.length())), fl.substring(0,Math.min(fl.length(),s.length())));
                        if (dist < bestD) { bestD = dist; best = i+1; }
                    }
                }
                if (best > 0) d.append("最相似: 第").append(best).append("行: ").append(cls[best-1].strip()).append("\n");
                d.append("提示: old_string 必须与文件完全一致，含空白和缩进。");
                return d.toString();
            }

            int count = 0, pos = 0;
            while ((pos = content.indexOf(old_string, pos)) != -1) { count++; pos += old_string.length(); }
            if (!replaceAll && count > 1)
                return "Error: old_string 出现 " + count + " 次（不唯一）。请设置 replace_all=true 或提供更长的上下文。";

            String result = replaceAll ? content.replace(old_string, new_string)
                    : content.replaceFirst(Pattern.quote(old_string), java.util.regex.Matcher.quoteReplacement(new_string));
            Files.writeString(path, result, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            int repl = replaceAll ? count : 1;
            StringBuilder sb = new StringBuilder();
            sb.append("✅ 文件编辑成功\n   文件: ").append(path).append("\n   替换: ").append(repl).append(" 处\n");
            int ci = result.indexOf(new_string);
            if (ci >= 0) {
                String[] rl = result.split("\n"); int cl = 0;
                for (int i = 0; i < Math.min(ci, result.length()); i++) if (result.charAt(i)=='\n') cl++;
                int cs = Math.max(0, cl-3), ce = Math.min(rl.length, cl+4);
                sb.append("   变更上下文:\n");
                for (int i = cs; i < ce; i++)
                    sb.append(String.format("   %s %6d\t%s%n", (i==cl?"▶":" "), i+1, rl[i]));
            }
            return sb.toString();
        } catch (IOException e) { return "Error: 编辑失败: " + e.getMessage(); }
    }

    private int lev(String a, String b) {
        int[][] dp = new int[a.length()+1][b.length()+1];
        for (int i=0;i<=a.length();i++) dp[i][0]=i;
        for (int j=0;j<=b.length();j++) dp[0][j]=j;
        for (int i=1;i<=a.length();i++)
            for (int j=1;j<=b.length();j++)
                dp[i][j] = Math.min(Math.min(dp[i-1][j]+1,dp[i][j-1]+1), dp[i-1][j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
        return dp[a.length()][b.length()];
    }
}
