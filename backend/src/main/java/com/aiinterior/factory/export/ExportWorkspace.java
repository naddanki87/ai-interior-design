package com.aiinterior.factory.export;

import com.aiinterior.factory.common.Ids;
import com.aiinterior.factory.config.FactoryProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Export folder layout: {@code <factory.export.output-dir>/<sessionId>/v<designVersion>/}. */
@Component
public class ExportWorkspace {

    public static final Set<String> DOWNLOADABLE = Set.of(
            "model.skp", "build_model.rb", "job.json", "build_manifest.json", "geometry.json", "model.obj",
            "sketchup_result.json", "floor_plan.svg", "boq.csv");

    private final Path root;

    public ExportWorkspace(FactoryProperties props) {
        this.root = Path.of(props.export().outputDir()).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public Path dir(String sessionId, int version) throws IOException {
        if (!Ids.isSafe(sessionId)) {
            throw new IllegalArgumentException("Invalid session id");
        }
        Path d = root.resolve(sessionId).resolve("v" + version).normalize();
        if (!d.startsWith(root)) {
            throw new IllegalArgumentException("Invalid export path");
        }
        Files.createDirectories(d);
        return d;
    }

    /** Only whitelisted file names inside the session folder can be downloaded. */
    public Path resolveDownload(String sessionId, int version, String name) throws IOException {
        if (!DOWNLOADABLE.contains(name)) {
            throw new IllegalArgumentException("File not available: " + name);
        }
        return dir(sessionId, version).resolve(name);
    }
}
