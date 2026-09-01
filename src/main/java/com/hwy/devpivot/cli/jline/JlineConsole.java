package com.hwy.devpivot.cli.jline;

import com.hwy.devpivot.agent.AgentChat;
import com.hwy.devpivot.cli.CliConsoleManager;
import com.hwy.devpivot.cli.ConsoleSession;
import com.hwy.devpivot.cli.InputCollector;
import com.hwy.devpivot.cli.UserInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jline.keymap.KeyMap;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JLine 3 interactive REPL with:
 * <ul>
 *   <li>Smart Enter + bracketed paste detection</li>
 *   <li>Alt+Enter for manual newline insertion</li>
 *   <li>Auto-completion on {@code /} and {@code @} (Tab to select)</li>
 *   <li>Drag-and-drop file path → auto {@code @} prefix</li>
 *   <li>Multi-line paste → inline summary</li>
 * </ul>
 */
public class JlineConsole implements ConsoleSession {

    private static final Logger logger = LoggerFactory.getLogger(JlineConsole.class);

    private static final String PROMPT_PRIMARY    = "> ";
    private static final String PROMPT_SECONDARY  = ". ";
    private static final String HISTORY_FILENAME  = ".cli-history";
    private static final long   PASTE_THRESHOLD_MS = 80;

    private static final Pattern PASTE_MARKER =
            Pattern.compile("\\[Pasted text #(\\d+) [+-]?\\d+ lines?]");
    private static final Pattern ABS_PATH =
            Pattern.compile("(?<!@)(\")?([A-Za-z]:[/\\\\][^\"\\s]+|/(?:[^\"\\s]+/)+[^\"\\s]+)(\")?");
    private static final Pattern CMD_HIGHLIGHT =
            Pattern.compile("^/(\\w+).*");
    private static final Pattern AT_PATH =
            Pattern.compile("@([^\\s]+)");

    private final InputCollector   collector;
    private final JlineCommandCompleter completer;
    private final Terminal         terminal;
    private final LineReader       reader;
    private final Path             historyPath;

    private volatile boolean running       = false;
    private volatile boolean crHandled     = false;
    private volatile long    lastNewlineMs = 0;

    private final Map<Integer, String> pasteStore  = new HashMap<>();
    private       int                  pasteCounter;

    /** 会话 ID，启动时生成，多轮对话复用 */
    private final String conversationId = UUID.randomUUID().toString();

    private boolean manualAtTyped;

    public JlineConsole() throws IOException {
        this.collector = new InputCollector();
        this.completer = new JlineCommandCompleter();
        this.terminal = TerminalBuilder.builder()
                .encoding("UTF-8")
                .system(true)
                .jansi(true).build();
        this.historyPath = resolveHistoryPath();

        DefaultParser parser = new DefaultParser();
        parser.setEscapeChars(null);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> CliConsoleManager.remove(conversationId)));
        CliConsoleManager.register(conversationId, this);

        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(completer)
                .parser(parser)
                .highlighter(new CliHighlighter())
                .history(new DefaultHistory())
                .option(LineReader.Option.BRACKETED_PASTE, true)
                .option(LineReader.Option.AUTO_FRESH_LINE, true)
                .option(LineReader.Option.AUTO_MENU_LIST, true)
                .option(LineReader.Option.HISTORY_IGNORE_SPACE, true)
                .option(LineReader.Option.CASE_INSENSITIVE, true)
                .variable(LineReader.SECONDARY_PROMPT_PATTERN, PROMPT_SECONDARY)
                .variable(LineReader.HISTORY_FILE, historyPath)
                .variable(LineReader.HISTORY_FILE_SIZE, 1000)
                .variable(LineReader.INDENTATION, 0)
                .variable(LineReader.LIST_MAX, 0)
                .variable(LineReader.BELL_STYLE, "none")
                .build();

        registerNewlineWidget();
        registerSmartEnter();
        registerAutoCompleteTriggers();
    }

    // ── Alt+Enter → literal newline ──────────────────
    private void registerNewlineWidget() {
        Widget w = () -> { reader.getBuffer().write('\n'); return true; };
        reader.getWidgets().put("insert-newline", w);
        KeyMap<Binding> km = reader.getKeyMaps().get(LineReader.MAIN);
        km.bind(new Reference("insert-newline"), "\033\r");
        km.bind(new Reference("insert-newline"), "\033\n");
    }

    // ── Smart Enter: paste vs manual submit ──────────
    private void registerSmartEnter() {
        Widget crHandler = () -> {
            long now = System.currentTimeMillis();
            if (inputAvailable() || isLikelyPaste(now)) {
                recordNewline(now);
                reader.getBuffer().write('\n');
                crHandled = true;
                return true;
            }
            crHandled = false;
            reader.callWidget(LineReader.ACCEPT_LINE);
            return true;
        };

        Widget lfHandler = () -> {
            long now = System.currentTimeMillis();
            if (inputAvailable() || isLikelyPaste(now)) {
                recordNewline(now);
                reader.getBuffer().write('\n');
                return true;
            }
            if (crHandled) {
                crHandled = false;
                collapseBufferAfterPasteIfNeeded(now);
                return true;
            }
            if (reader.getBuffer().length() == 0) return true;
            reader.callWidget(LineReader.ACCEPT_LINE);
            return true;
        };

        reader.getWidgets().put("__smart_cr", crHandler);
        reader.getWidgets().put("__smart_lf", lfHandler);
        KeyMap<Binding> km = reader.getKeyMaps().get(LineReader.MAIN);
        km.bind(new Reference("__smart_cr"), "\r");
        km.bind(new Reference("__smart_lf"), "\n");
    }

    private void recordNewline(long now)    { lastNewlineMs = now; }
    private boolean isLikelyPaste(long now) { return lastNewlineMs > 0 && (now - lastNewlineMs) < PASTE_THRESHOLD_MS; }
    private boolean inputAvailable()        { try { return terminal.input().available() > 0; } catch (IOException e) { return false; } }

    private void collapseBufferAfterPasteIfNeeded(long now) {
        if (inputAvailable() || isLikelyPaste(now)) return;
        Buffer buf = reader.getBuffer();
        String content = buf.toString();
        if (content.isEmpty() || (!content.contains("\n") && !content.contains("\r"))) return;
        int id = ++pasteCounter;
        pasteStore.put(id, content);
        String marker = String.format("[Pasted text #%d +%d lines]", id, content.split("\\R", -1).length);
        buf.cursor(0);
        try { buf.delete(buf.length()); } catch (Exception e) { reader.callWidget("kill-whole-line"); }
        buf.write(marker);
    }

    // ── / and @ auto-trigger completion ──────────────
    private void registerAutoCompleteTriggers() {
        Widget slashTrigger = () -> {
            reader.getBuffer().write('/');
            if (!inputAvailable()) reader.callWidget(LineReader.COMPLETE_WORD);
            return true;
        };
        Widget atTrigger = () -> {
            reader.getBuffer().write('@');
            if (!inputAvailable()) {
                manualAtTyped = true;
                reader.callWidget(LineReader.COMPLETE_WORD);
            }
            return true;
        };

        reader.getWidgets().put("__slash_trigger", slashTrigger);
        reader.getWidgets().put("__at_trigger",    atTrigger);

        KeyMap<Binding> km = reader.getKeyMaps().get(LineReader.MAIN);
        km.bind(new Reference("__slash_trigger"), "/");
        km.bind(new Reference("__at_trigger"),    "@");
    }

    // ── Paste marker expand / collapse ──────────────

    private String collapseMultiline(String line) {
        if (line == null || (!line.contains("\n") && !line.contains("\r"))) return line;
        int id = ++pasteCounter;
        pasteStore.put(id, line);
        return String.format("[Pasted text #%d +%d lines]", id, line.split("\\R", -1).length);
    }

    private String expandPasteMarkers(String input) {
        Matcher m = PASTE_MARKER.matcher(input);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        while (m.find()) {
            sb.append(input, lastEnd, m.start());
            String full = pasteStore.get(Integer.parseInt(m.group(1)));
            sb.append(full != null ? full : m.group());
            lastEnd = m.end();
        }
        return sb.append(input, lastEnd, input.length()).toString();
    }

    // ── @ path → absolute path ──────────────────────

    /** 手动输入 @path 时解析为绝对路径。粘贴的 @ 不处理。 */
    private String resolveAtPaths(String input) {
        if (!manualAtTyped || input == null) return input;
        manualAtTyped = false;
        Matcher m = AT_PATH.matcher(input);
        if (!m.find()) return input;
        m.reset();
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        while (m.find()) {
            sb.append(input, lastEnd, m.start());
            String fullMatch = m.group();
            String path = m.group(1);
            // 从末尾逐字符截短，找到最长的存在路径（处理后缀文字粘连问题）
            Path abs = null;
            String validPath = null;
            for (int len = path.length(); len > 0; len--) {
                String sub = path.substring(0, len);
                if (sub.endsWith("/") || sub.endsWith("\\")) continue;
                try {
                    Path p = Paths.get(sub);
                    if (!p.isAbsolute()) {
                        p = Paths.get("").toAbsolutePath().resolve(sub);
                    }
                    p = p.normalize();
                    if (Files.exists(p)) {
                        abs = p;
                        validPath = sub;
                        break;
                    }
                } catch (Exception ignored) {}
            }
            if (abs != null) {
                sb.append('`').append(abs.toString()).append('`');
                // 只跳过已解析的部分，保留粘连的后缀文字
                int consumed = m.start() + 1 + validPath.length();
                sb.append(input, consumed, m.end());
            } else {
                sb.append(fullMatch);
            }
            lastEnd = m.end();
        }
        sb.append(input, lastEnd, input.length());
        return sb.toString();
    }

    // ── Drag-drop path → @ prefix ───────────────────

    static String normalizeDroppedPaths(String input) {
        Matcher m = ABS_PATH.matcher(input);
        if (!m.find()) return input;
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        m.reset();
        while (m.find()) {
            sb.append(input, lastEnd, m.start());
            if (m.group(1) != null) sb.append(m.group(1));
            sb.append('@').append(m.group(2));
            if (m.group(3) != null) sb.append(m.group(3));
            lastEnd = m.end();
        }
        return sb.append(input, lastEnd, input.length()).toString();
    }

    // ── REPL loop ───────────────────────────────────
    public void start() {
        running = true;
        logger.debug("CliConsole REPL started, terminal={}", terminal.getType());
        printWelcome();
        while (running) {
            try {
                manualAtTyped = false;
                String line = reader.readLine(PROMPT_PRIMARY);
                if (line == null) break;
                line = line.stripTrailing();
                if (line.isEmpty()) continue;
                if (handleBuiltin(line)) continue;
                logger.debug("cliReadInput={}",line);
                line = normalizeDroppedPaths(line);
                String displayLine = collapseMultiline(line);
                String fullLine    = expandPasteMarkers(displayLine);
                String resolvedInput = resolveAtPaths(fullLine);
                extracted(resolvedInput);
                collector.addAuto(displayLine);

                try {
                    logger.debug("cliResolveInput={}",resolvedInput);

                    new Thread(() -> {
                        try {
                            String result = AgentChat.chat(resolvedInput, conversationId);
                            if (result != null && !result.isEmpty()) {
                                reader.printAbove(result);
                            }
                        } catch (Exception e) {
                            logger.error("AgentMainBoot 执行异常", e);
                            reader.printAbove("Error: " + e.getMessage());
                        }
                    }).start();

                } catch (Exception e) {
                    logger.error("AgentMainBoot 执行异常", e);
                    terminal.writer().println("Error: " + e.getMessage());
                    terminal.writer().flush();
                }

            } catch (UserInterruptException e) {
                terminal.writer().println("^C");
                terminal.writer().flush();
            } catch (EndOfFileException e) {
                break;
            }
        }
        printGoodbye();
    }

    private void extracted(String resolvedInput) {
        completer.addToHistory(resolvedInput);
    }

    public void stop() { running = false; }

    // ── Clear screen ─────────────────────────────────

    private void clearScreen() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                new ProcessBuilder("sh", "-c", "clear").inheritIO().start().waitFor();
            }
        } catch (Exception e) {
            terminal.writer().print("\033[2J\033[H\033[3J");
            terminal.writer().flush();
        }
    }

    // ── Built-in commands ───────────────────────────
    private boolean handleBuiltin(String line) {
        return switch (line.strip()) {
            case "/exit", "/quit"  -> { stop(); yield true; }
            case "/clear"          -> { clearScreen(); yield true; }
            case "/help"           -> { printHelp();    yield true; }
            case "/history"        -> { printHistory(); yield true; }
            case "/status"         -> { printStatus();  yield true; }
            case "/reset"          -> { collector.clear(); terminal.writer().println("[Session reset]"); terminal.writer().flush(); yield true; }
            default                -> false;
        };
    }

    // ── UI ──────────────────────────────────────────
    private void printWelcome() {
        AttributedStyle s = new AttributedStyle().foreground(AttributedStyle.CYAN).bold();
        String banner = """
                +------------------------------------------+
                |     DevPivot CLI  --  JLine REPL         |
                +------------------------------------------+
                |  Tab       ->  command autocomplete      |
                |  Alt+Enter ->  insert newline            |
                |  /help     ->  show help                 |
                |  /exit     ->  quit                      |
                +------------------------------------------+
                """;
        terminal.writer().println(new AttributedString(banner, s).toAnsi());
        terminal.writer().flush();
    }

    private void printGoodbye() { logger.debug("CliConsole REPL stopped"); terminal.writer().println("\nBye!"); terminal.writer().flush(); }

    private void printHelp() {
        terminal.writer().println("""
                Built-in commands:
                  /help       show this help
                  /exit       quit program
                  /clear      clear screen
                  /history    show input history
                  /status     show session status
                  /reset      reset session

                Key bindings:
                  Enter       submit input
                  Alt+Enter   insert newline (multi-line)
                  Tab         autocomplete / select candidate
                  Up / Down   browse history
                  Ctrl+C      interrupt
                  Ctrl+D      EOF / exit
                  Ctrl+L      clear screen

                Completion prefixes:
                  /           slash command completion
                  @           file path completion
                """);
        terminal.writer().flush();
    }

    private void printHistory() {
        var all = collector.getAll();
        if (all.isEmpty()) { terminal.writer().println("[No input history]"); terminal.writer().flush(); return; }
        terminal.writer().println("\n-- Input History (" + all.size() + " items) --");
        for (UserInput ui : all) {
            String preview = ui.getText().replace("\n", "~");
            if (preview.length() > 80) preview = preview.substring(0, 80) + "...";
            terminal.writer().printf("  #%d [%s] %s%n",
                    ui.getId(), ui.getTimestamp().toString().replace("T", " "), preview);
        }
        terminal.writer().println();
        terminal.writer().flush();
    }

    private void printStatus() {
        terminal.writer().println();
        terminal.writer().printf("  Session ID : %s%n", collector.getSessionId());
        terminal.writer().printf("  Start time : %s%n", collector.getStartTime().toString().replace("T", " "));
        terminal.writer().printf("  Input count: %d%n", collector.count());
        terminal.writer().printf("  Term type  : %s%n", terminal.getType());
        terminal.writer().printf("  Term size  : %d x %d%n", terminal.getHeight(), terminal.getWidth());
        terminal.writer().println();
        terminal.writer().flush();
    }

    private Path resolveHistoryPath() throws IOException {
        Path dir = Paths.get(System.getProperty("user.home"), ".dev-pivot");
        Files.createDirectories(dir);
        return dir.resolve(HISTORY_FILENAME);
    }

    // ── Highlighter ─────────────────────────────────
    private static class CliHighlighter implements Highlighter {
        @Override
        public AttributedString highlight(LineReader reader, String buffer) {
            if (CMD_HIGHLIGHT.matcher(buffer.stripLeading()).matches()) {
                return new AttributedString(buffer,
                        new AttributedStyle().foreground(AttributedStyle.YELLOW).bold());
            }
            return AttributedString.fromAnsi(buffer);
        }
        @Override public void setErrorPattern(Pattern p) {}
        @Override public void setErrorIndex(int i)   {}
    }

    // ── Getters ─────────────────────────────────────
    @Override
    public String getConversationId()    { return conversationId; }
    public InputCollector getCollector() { return collector; }
    public Terminal       getTerminal()  { return terminal; }

    // ── ConsoleSession output ────────────────────────
    @Override
    public void print(String text) {
        terminal.writer().print(text);
        this.flush();
    }

    @Override
    public void println() {
        terminal.writer().println();
        this.flush();
    }

    @Override
    public void println(String text) {
        terminal.writer().println(text);
        this.flush();
    }

    @Override
    public void flush() { terminal.writer().flush(); }

    // ── Entry point ─────────────────────────────────
    public static void main(String[] args) {
        try {
            JlineConsole console = new JlineConsole();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { console.terminal.close(); } catch (Exception ignored) {}
            }));
            console.start();
        } catch (IOException e) {
            logger.error("Failed to init terminal", e);
            System.exit(1);
        }
    }
}
