package com.aiinterior.factory.export;

import com.aiinterior.factory.common.Hashing;
import com.aiinterior.factory.geometry.BuildManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Verifies that a file really is a native SketchUp model:
 * 1. it exists and is not empty,
 * 2. it starts with the SketchUp file signature (0xFF 0xFE 0xFF, length byte, then
 *    "SketchUp Model" in UTF-16LE),
 * 3. the bounds SketchUp reported after building match the manifest bounds (±2 mm).
 * A renamed OBJ/GLB/Blender file fails check 2.
 */
@Component
public class SkpVerifier {

    static final byte[] SIGNATURE_PREFIX = {(byte) 0xFF, (byte) 0xFE, (byte) 0xFF};
    static final byte[] SIGNATURE_TEXT = "SketchUp Model".getBytes(StandardCharsets.UTF_16LE);

    private final ObjectMapper mapper;

    public SkpVerifier(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public SkpVerification verify(Path skp, Path resultJson, BuildManifest manifest) {
        List<String> messages = new ArrayList<>();
        Map<String, Object> report = readReport(resultJson, messages);
        if (!Files.isRegularFile(skp)) {
            if (report != null && !"SAVED".equals(report.get("status"))) {
                messages.add("SketchUp reported " + report.get("status") + ": " + report.getOrDefault("error", ""));
            } else {
                messages.add("No .skp file at " + skp.getFileName());
            }
            return new SkpVerification(SkpVerification.Status.NOT_PRODUCED, false, 0, false, null, null, report, messages);
        }
        try {
            long size = Files.size(skp);
            byte[] head;
            try (InputStream in = Files.newInputStream(skp)) {
                head = in.readNBytes(64);
            }
            boolean header = hasNativeHeader(head);
            if (!header) {
                messages.add("File does not start with the SketchUp signature - it is not a native .skp");
            }
            Boolean bounds = null;
            if (report != null && report.get("boundsMm") instanceof Map<?, ?> b) {
                double[] expected = extent(manifest);
                bounds = close(b.get("x"), expected[0]) && close(b.get("y"), expected[1]) && close(b.get("z"), expected[2]);
                if (!bounds) {
                    messages.add("Model bounds " + b + " differ from expected " + Arrays.toString(expected) + " mm");
                }
            } else {
                messages.add("No SketchUp build report found - bounds not cross-checked");
            }
            boolean ok = size > 0 && header && !Boolean.FALSE.equals(bounds);
            if (ok) {
                messages.add("Native SketchUp signature present" + (bounds == null ? "" : "; bounds match the geometry"));
            }
            return new SkpVerification(ok ? SkpVerification.Status.VERIFIED : SkpVerification.Status.FAILED, true, size,
                    header, sha256(skp), bounds, report, messages);
        } catch (IOException e) {
            messages.add("Cannot read .skp: " + e.getMessage());
            return new SkpVerification(SkpVerification.Status.FAILED, true, 0, false, null, null, report, messages);
        }
    }

    static boolean hasNativeHeader(byte[] head) {
        int n = SIGNATURE_PREFIX.length + 1 + SIGNATURE_TEXT.length;
        if (head.length < n) {
            return false;
        }
        for (int i = 0; i < SIGNATURE_PREFIX.length; i++) {
            if (head[i] != SIGNATURE_PREFIX[i]) {
                return false;
            }
        }
        // head[3] is the character-count byte of the following UTF-16 string.
        return Arrays.equals(Arrays.copyOfRange(head, 4, 4 + SIGNATURE_TEXT.length), SIGNATURE_TEXT);
    }

    public static double[] extent(BuildManifest m) {
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (BuildManifest.Box b : m.boxes()) {
            for (int i = 0; i < 3; i++) {
                lo[i] = Math.min(lo[i], b.min()[i]);
                hi[i] = Math.max(hi[i], b.max()[i]);
            }
        }
        return new double[]{hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]};
    }

    private static boolean close(Object reported, double expected) {
        return reported instanceof Number n && Math.abs(n.doubleValue() - expected) <= 2.0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readReport(Path resultJson, List<String> messages) {
        if (resultJson == null || !Files.isRegularFile(resultJson)) {
            return null;
        }
        try {
            return mapper.readValue(resultJson.toFile(), Map.class);
        } catch (IOException e) {
            messages.add("SketchUp result file unreadable: " + e.getMessage());
            return null;
        }
    }

    private static String sha256(Path p) throws IOException {
        MessageDigest md = Hashing.sha256();
        try (InputStream in = Files.newInputStream(p)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) {
                md.update(buf, 0, r);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
