package com.aiinterior.factory.export;

import com.aiinterior.factory.config.FactoryProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Launches the configured SketchUp executable with {@code -RubyStartup build_model.rb} and waits
 * (bounded) for the Ruby builder to write its result file. Only the configured executable can be
 * started, with fixed arguments - agents cannot run arbitrary commands.
 */
@Component
public class SketchUpLauncher {

    private static final Logger log = LoggerFactory.getLogger(SketchUpLauncher.class);

    public enum Outcome { RESULT_WRITTEN, TIMED_OUT, NOT_CONFIGURED, LAUNCH_FAILED }

    private final FactoryProperties.SketchUp cfg;

    public SketchUpLauncher(FactoryProperties props) {
        this.cfg = props.sketchup();
    }

    public boolean configured() {
        return cfg.configured() && Files.isRegularFile(Path.of(cfg.executable()));
    }

    public String describe() {
        if (!cfg.configured()) {
            return "not configured (factory.sketchup.executable is empty)";
        }
        return Files.isRegularFile(Path.of(cfg.executable())) ? cfg.executable() : "configured path not found: " + cfg.executable();
    }

    public Outcome run(Path script, Path resultFile, Path workDir) {
        if (!configured()) {
            return Outcome.NOT_CONFIGURED;
        }
        Process p;
        try {
            Files.deleteIfExists(resultFile);
            p = new ProcessBuilder(List.of(cfg.executable(), cfg.startupFlag(), script.toString()))
                    .directory(workDir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(workDir.resolve("sketchup_console.log").toFile())
                    .start();
        } catch (IOException e) {
            log.warn("Could not launch SketchUp: {}", e.getMessage());
            return Outcome.LAUNCH_FAILED;
        }
        long deadline = System.currentTimeMillis() + cfg.timeout().toMillis();
        long exitedAt = -1;
        try {
            while (System.currentTimeMillis() < deadline) {
                if (Files.isRegularFile(resultFile) && Files.size(resultFile) > 0) {
                    return Outcome.RESULT_WRITTEN;
                }
                // Some launchers hand over to a child process and exit at once, so allow a grace period.
                if (!p.isAlive()) {
                    if (exitedAt < 0) {
                        exitedAt = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - exitedAt > 20_000) {
                        return Outcome.LAUNCH_FAILED;
                    }
                }
                Thread.sleep(1000);
            }
        } catch (IOException e) {
            return Outcome.LAUNCH_FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (p.isAlive() && cfg.quitWhenDone()) {
                p.destroy();
            }
        }
        return Outcome.TIMED_OUT;
    }
}
