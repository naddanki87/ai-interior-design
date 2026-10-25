package com.aiinterior.factory.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiinterior.factory.geometry.BuildManifest;
import com.aiinterior.factory.geometry.BuildManifestFactory;
import com.aiinterior.factory.geometry.TestRooms;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkpVerifierTest {

    @TempDir
    Path dir;

    private final ObjectMapper mapper = new ObjectMapper();
    private final SkpVerifier verifier = new SkpVerifier(mapper);
    private final BuildManifest manifest = new BuildManifestFactory().build(TestRooms.shell(List.of(), List.of()));

    private static byte[] skpHeader() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0xFF, 0x0E});
        out.write("SketchUp Model".getBytes(StandardCharsets.UTF_16LE));
        out.write(new byte[64]);
        return out.toByteArray();
    }

    private void writeResult(double x, double y, double z) throws Exception {
        mapper.writeValue(dir.resolve("result.json").toFile(), java.util.Map.of("status", "SAVED",
                "boundsMm", java.util.Map.of("x", x, "y", y, "z", z)));
    }

    @Test
    void missingFileIsNotProduced() {
        var v = verifier.verify(dir.resolve("model.skp"), dir.resolve("result.json"), manifest);
        assertThat(v.status()).isEqualTo(SkpVerification.Status.NOT_PRODUCED);
    }

    @Test
    void nativeHeaderAndMatchingBoundsVerify() throws Exception {
        Files.write(dir.resolve("model.skp"), skpHeader());
        double[] e = SkpVerifier.extent(manifest);
        writeResult(e[0], e[1], e[2]);
        var v = verifier.verify(dir.resolve("model.skp"), dir.resolve("result.json"), manifest);
        assertThat(v.status()).isEqualTo(SkpVerification.Status.VERIFIED);
        assertThat(v.nativeHeader()).isTrue();
        assertThat(v.boundsMatch()).isTrue();
        assertThat(e).containsExactly(5430.0, 4330.0, 3050.0);
    }

    @Test
    void renamedObjIsRejected() throws Exception {
        Files.writeString(dir.resolve("model.skp"), "# OBJ\nv 0 0 0\n");
        var v = verifier.verify(dir.resolve("model.skp"), null, manifest);
        assertThat(v.status()).isEqualTo(SkpVerification.Status.FAILED);
        assertThat(v.nativeHeader()).isFalse();
    }

    @Test
    void wrongBoundsFail() throws Exception {
        Files.write(dir.resolve("model.skp"), skpHeader());
        writeResult(1000, 1000, 1000);
        var v = verifier.verify(dir.resolve("model.skp"), dir.resolve("result.json"), manifest);
        assertThat(v.status()).isEqualTo(SkpVerification.Status.FAILED);
        assertThat(v.boundsMatch()).isFalse();
    }

    @Test
    void objWriterOutputMatchesManifest() {
        String obj = ObjWriter.write(manifest);
        assertThat(ObjWriter.verify(obj, manifest)).isTrue();
        assertThat(ObjWriter.verify(obj + "f 1 2 3 4\n", manifest)).isFalse();
    }

    @Test
    void wallsAreSplitAroundOpenings() {
        // SOUTH wall with a door -> left + right + lintel; EAST wall with window -> left, sill, head, right.
        assertThat(manifest.boxes().stream().filter(b -> b.id().startsWith("wall-s"))).hasSize(3);
        assertThat(manifest.boxes().stream().filter(b -> b.id().startsWith("wall-e"))).hasSize(4);
        assertThat(manifest.boxes().stream().filter(b -> b.id().startsWith("wall-n"))).hasSize(1);
    }
}
