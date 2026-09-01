package com.hwy.devpivot.cli.jline;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Command autocompleter — implements JLine {@link Completer}.
 *
 * <p>Three completion modes:
 * <ul>
 *   <li><b>/</b> — slash commands (e.g. /help, /exit)</li>
 *   <li><b>@</b> — file/directory picker with fuzzy matching</li>
 *   <li><b>free text</b> — prefix match against input history</li>
 * </ul>
 */
public class JlineCommandCompleter implements Completer {

    private static final int MAX_CANDIDATES  = 50;
    private static final int MAX_FILES_PER_DIR = 500;

    private final Set<String> lastAtCandidates = new LinkedHashSet<>();

    /** 最近一次 @ 补全生成的候选项（含 @ 前缀），用于判断路径是否来自补全选择。 */
    public Set<String> getLastAtCandidates() {
        return lastAtCandidates;
    }

    // ── Slash commands ───────────────────────────────
    private static final LinkedHashMap<String, String> COMMANDS = new LinkedHashMap<>();

    static {
        COMMANDS.put("/help",    "Show help");
        COMMANDS.put("/exit",    "Quit");
        COMMANDS.put("/quit",    "Quit (alias)");
        COMMANDS.put("/clear",   "Clear screen");
        COMMANDS.put("/memory",  "Manage memory / context");
        COMMANDS.put("/model",   "Switch or view model");
        COMMANDS.put("/file",    "Add file to context");
        COMMANDS.put("/paste",   "Enter multi-line paste mode");
        COMMANDS.put("/config",  "View / edit config");
        COMMANDS.put("/history", "Show input history");
        COMMANDS.put("/reset",   "Reset session");
        COMMANDS.put("/cost",    "Token usage stats");
        COMMANDS.put("/status",  "Session status");
        COMMANDS.put("/init",    "Initialize CLAUDE.md");
        COMMANDS.put("/review",  "Code review changes");
        COMMANDS.put("/doctor",  "Diagnose environment");
        COMMANDS.put("/compact", "Compact context");
    }

    // ── Input history cache ──────────────────────────
    private final LinkedList<String> historyBuffer = new LinkedList<>();
    private static final int MAX_HISTORY = 200;

    public void addToHistory(String line) {
        if (line == null || line.isBlank()) return;
        historyBuffer.remove(line);
        historyBuffer.addLast(line);
        if (historyBuffer.size() > MAX_HISTORY) {
            historyBuffer.removeFirst();
        }
    }

    // ── Main callback ────────────────────────────────
    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = reader.getBuffer().toString();
        int cursor = reader.getBuffer().cursor();
        String textBeforeCursor = buffer.substring(0, cursor);

        // Find last @ in text before cursor — it defines the file-search segment
        int atIdx = textBeforeCursor.lastIndexOf('@');
        // Find last / that starts a command (must be at position 0 or after space)
        int slashIdx = -1;
        for (int i = textBeforeCursor.length() - 1; i >= 0; i--) {
            char ch = textBeforeCursor.charAt(i);
            if (ch == '/' && (i == 0 || textBeforeCursor.charAt(i - 1) == ' ')) {
                slashIdx = i;
                break;
            }
        }

        // @ takes priority if it appears after the slash
        if (atIdx >= 0 && atIdx >= slashIdx) {
            completeAtMention(textBeforeCursor.substring(atIdx), candidates);
        } else if (slashIdx >= 0) {
            completeSlashCommand(textBeforeCursor.substring(slashIdx), candidates);
        } else {
            completeFreeText(textBeforeCursor, candidates);
        }

    }

    // ── /  Slash commands ────────────────────────────
    private void completeSlashCommand(String prefix, List<Candidate> candidates) {
        String lower = prefix.toLowerCase();
        for (Map.Entry<String, String> e : COMMANDS.entrySet()) {
            if (e.getKey().toLowerCase().startsWith(lower)) {
                candidates.add(new Candidate(
                        e.getKey(), e.getKey(), null, e.getValue(),
                        null, null, false));
            }
        }
    }

    // ── @  File / directory picker ───────────────────
    private void completeAtMention(String raw, List<Candidate> candidates) {
        // Strip leading @
        String query = raw.substring(1);   // e.g. "c:/temp/te" or "c:temp/test" or "pom"

        // ★ Normalize: "c:temp/test" → "c:/temp/test" (add / after drive letter)
        query = normalizeWindowsDrivePath(query);

        // Resolve base directory and partial-name filter
        String baseDir;     // directory to list
        String nameFilter;  // partial file name to match (lowercase)

        int lastSlash = Math.max(query.lastIndexOf('/'), query.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            baseDir    = query.substring(0, lastSlash + 1);   // "c:/temp/" or "src/main/"
            nameFilter = query.substring(lastSlash + 1).toLowerCase();  // "te"
        } else {
            // No slash in query. Could be "pom", "c:", or "D:"
            if (isWindowsDrive(query)) {
                baseDir    = query + "/";
                nameFilter = "";
            } else {
                baseDir    = "";
                nameFilter = query.toLowerCase();
            }
        }

        // Resolve to absolute dir
        Path searchRoot = resolveSearchRoot(baseDir);
        if (searchRoot == null || !Files.isDirectory(searchRoot)) return;

        // List files in searchRoot only (no recursion)
        List<FileEntry> matches = new ArrayList<>();
        collectFiles(searchRoot.toFile(), nameFilter, matches);

        // Sort: directories first, then by name
        matches.sort((a, b) -> {
            if (a.isDir != b.isDir) return a.isDir ? -1 : 1;
            return a.name.compareToIgnoreCase(b.name);
        });

        // Build candidates
        int count = 0;
        for (FileEntry entry : matches) {
            if (count++ >= MAX_CANDIDATES) break;

            String displayPath = (baseDir + entry.name).replace('\\', '/');
            String display     = "@" + displayPath;
            String suffix      = entry.isDir ? "/" : "";
            String desc        = entry.isDir ? "dir"  : String.format("file (%s)", humanSize(entry.size));

            candidates.add(new Candidate(
                    display, display, null, desc,
                    suffix, null, false));
        }

        // 记录本次补全的所有候选项，供 CliConsole 判断路径是否来自补全选择
        lastAtCandidates.clear();
        for (Candidate c : candidates) {
            lastAtCandidates.add(c.value());
            if (c.suffix() != null) {
                lastAtCandidates.add(c.value() + c.suffix());
            }
        }
    }

    /** Detect Windows drive letter: "c:" or "D:" (case insensitive). */
    private static boolean isWindowsDrive(String s) {
        return s.length() == 2
                && Character.isLetter(s.charAt(0))
                && s.charAt(1) == ':';
    }

    /**
     * Normalize: "c:temp/test" → "c:/temp/test", "D:projects" → "D:/projects".
     * Only applies when path starts with drive letter + non-slash.
     */
    private static String normalizeWindowsDrivePath(String query) {
        if (query.length() >= 3
                && Character.isLetter(query.charAt(0))
                && query.charAt(1) == ':'
                && query.charAt(2) != '/'
                && query.charAt(2) != '\\') {
            return query.substring(0, 2) + '/' + query.substring(2);
        }
        return query;
    }

    /** Resolve baseDir to an absolute Path. */
    private Path resolveSearchRoot(String baseDir) {
        if (baseDir.isEmpty()) {
            return Paths.get(".").toAbsolutePath().normalize();
        }

        // Handle ~/  or  ~\
        if (baseDir.startsWith("~")) {
            String home = System.getProperty("user.home");
            String rest = baseDir.length() > 1 ? baseDir.substring(1) : "";
            return Paths.get(home + rest).normalize();
        }

        // Handle Windows drive letter:  c:/  C:\  d:/path  etc.
        if (baseDir.length() >= 2
                && Character.isLetter(baseDir.charAt(0))
                && baseDir.charAt(1) == ':') {
            // Normalize: ensure drive letter is uppercase and use backslash
            String drive = baseDir.substring(0, 1).toUpperCase() + ":\\";
            String rest  = baseDir.length() > 2
                    ? baseDir.substring(2).replace('/', '\\')
                    : "";
            // Remove leading backslash from rest if present (avoid double backslash)
            if (rest.startsWith("\\")) {
                rest = rest.substring(1);
            }
            return Paths.get(drive + rest).normalize().toAbsolutePath();
        }

        // Unix absolute or Windows UNC
        Path p = Paths.get(baseDir);
        if (p.isAbsolute()) {
            return p.normalize();
        }

        // Relative path
        return Paths.get(".").resolve(p).normalize();
    }

    /** Collect matching files/dirs in a single directory (no recursion into subdirectories). */
    private void collectFiles(File dir, String filter, List<FileEntry> out) {
        File[] children;
        try {
            children = dir.listFiles();
        } catch (SecurityException e) {
            return;
        }
        if (children == null || children.length > MAX_FILES_PER_DIR) return;

        Arrays.sort(children, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File f : children) {
            String name = f.getName();
            if (name.startsWith(".")) continue;

            if (matchesFuzzy(name.toLowerCase(), filter)) {
                long size = 0;
                if (!f.isDirectory()) {
                    try { size = Files.size(f.toPath()); } catch (IOException ignored) { }
                }
                out.add(new FileEntry(name, f.isDirectory(), size));
            }
        }
    }

    /**
     * Lenient fuzzy match: progressively trims the filter tail until a match
     * is found.  e.g. "test.jav" → tries "test.jav", then "test.ja" which
     * matches "test.java".  Stops when more than 25% of the filter is trimmed.
     * An empty filter matches everything.
     */
    private boolean matchesFuzzy(String name, String filter) {
        if (filter.isEmpty()) return true;
        int maxTrim = filter.length() / 4;  // allow up to 25% trim
        for (int trim = 0; trim <= maxTrim; trim++) {
            String f = filter.substring(0, filter.length() - trim);
            if (f.isEmpty()) return true;
            int fi = 0;
            for (int i = 0; i < name.length() && fi < f.length(); i++) {
                if (name.charAt(i) == f.charAt(fi)) fi++;
            }
            if (fi == f.length()) return true;
        }
        return false;
    }

    // ── Free text (history) ──────────────────────────
    private void completeFreeText(String prefix, List<Candidate> candidates) {
        String lower = prefix.toLowerCase();
        Iterator<String> it = historyBuffer.descendingIterator();
        Set<String> seen = new HashSet<>();
        while (it.hasNext()) {
            String h = it.next();
            if (h.toLowerCase().startsWith(lower) && h.length() > prefix.length()) {
                if (seen.add(h)) {
                    candidates.add(new Candidate(h, h, null, "history", null, null, false));
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────
    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    // ── Data class ───────────────────────────────────
    private static class FileEntry {
        final String  name;
        final boolean isDir;
        final long    size;

        FileEntry(String name, boolean isDir, long size) {
            this.name = name;
            this.isDir = isDir;
            this.size = size;
        }
    }
}
