package com.jvmtop;

import java.io.File;
import java.time.Duration;
import java.util.Locale;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import com.jvmtop.tui.JvmTopApp;
import com.jvmtop.tui.JvmTopElement;

public class JvmTop {

    public static final String VERSION = JvmTopElement.VERSION;

    private static Logger logger;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        logger = Logger.getLogger("jvmtop");
        setupLogging();

        OptionParser parser = createOptionParser();
        OptionSet options = parser.parse(args);

        if (options.has("help")) {
            System.out.println("jvmtop " + VERSION + " - Java monitoring for the command-line");
            System.out.println("Usage: jvmtop [options...] [PID]");
            System.out.println();
            parser.printHelpOn(System.out);
            System.exit(0);
        }

        if (options.has("sysinfo")) {
            outputSystemProps();
            return;
        }

        if (options.has("verbose")) {
            logger.setLevel(Level.ALL);
        }

        Integer targetPid = null;
        if (options.hasArgument("pid")) {
            targetPid = (Integer) options.valueOf("pid");
        } else if (!options.nonOptionArguments().isEmpty()) {
            targetPid = Integer.valueOf((String) options.nonOptionArguments().get(0));
        }

        boolean profileMode = options.has("profile");
        Duration updateInterval = Duration.ofSeconds(1);
        if (options.hasArgument("delay")) {
            double delay = (Double) options.valueOf("delay");
            if (delay < 0.1) {
                throw new IllegalArgumentException("Delay cannot be set below 0.1");
            }
            updateInterval = Duration.ofMillis((long) (delay * 1000));
        }

        JvmTopApp.run(targetPid, profileMode, updateInterval);
    }

    private static OptionParser createOptionParser() {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(java.util.Arrays.asList("help", "?", "h"), "shows this help").forHelp();
        parser.accepts("once", "exit after first output iteration [deprecated, use -n 1]");
        parser.acceptsAll(java.util.Arrays.asList("n", "iteration"), "exit after n iterations")
                .withRequiredArg().ofType(Integer.class);
        parser.acceptsAll(java.util.Arrays.asList("d", "delay"), "delay between iterations (seconds)")
                .withRequiredArg().ofType(Double.class);
        parser.accepts("profile", "start CPU profiling mode");
        parser.accepts("sysinfo", "outputs diagnostic information");
        parser.accepts("verbose", "verbose mode");
        parser.acceptsAll(java.util.Arrays.asList("p", "pid"), "PID to connect to")
                .withRequiredArg().ofType(Integer.class);
        parser.acceptsAll(java.util.Arrays.asList("w", "width"), "console width")
                .withRequiredArg().ofType(Integer.class);
        return parser;
    }

    private static void outputSystemProps() {
        for (Object key : System.getProperties().keySet()) {
            System.out.println(key + "=" + System.getProperty(key.toString()));
        }
    }

    private static void setupLogging() throws Exception {
        File logDir = new File(System.getProperty("jvmtop.log.dir", "target"));
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        File logFile = new File(logDir, "jvmtop.log");
        FileHandler fileHandler = new FileHandler(logFile.getAbsolutePath(), true);
        fileHandler.setFormatter(new SimpleFormatter());
        logger.addHandler(fileHandler);
        logger.setLevel(Level.ALL);
        Logger.getLogger("").setLevel(Level.ALL);
    }

    public static Logger getLogger() {
        return logger;
    }
}
