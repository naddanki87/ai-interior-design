package com.aiinterior.factory.export;

import com.aiinterior.factory.geometry.BuildManifest;
import java.util.Locale;

/** Wavefront OBJ (mm) - an open intermediate format, NOT a SketchUp file. */
public final class ObjWriter {

    private ObjWriter() {
    }

    public static String write(BuildManifest m) {
        StringBuilder sb = new StringBuilder("# AI Interior Design Factory - units: millimetres, Z up\n");
        int base = 1;
        for (BuildManifest.Box b : m.boxes()) {
            sb.append("o ").append(b.id()).append('\n');
            double[] lo = b.min();
            double[] hi = b.max();
            double[][] v = {
                    {lo[0], lo[1], lo[2]}, {hi[0], lo[1], lo[2]}, {hi[0], hi[1], lo[2]}, {lo[0], hi[1], lo[2]},
                    {lo[0], lo[1], hi[2]}, {hi[0], lo[1], hi[2]}, {hi[0], hi[1], hi[2]}, {lo[0], hi[1], hi[2]}};
            for (double[] p : v) {
                sb.append(String.format(Locale.ROOT, "v %.1f %.1f %.1f%n", p[0], p[1], p[2]));
            }
            int[][] faces = {{1, 4, 3, 2}, {5, 6, 7, 8}, {1, 2, 6, 5}, {2, 3, 7, 6}, {3, 4, 8, 7}, {4, 1, 5, 8}};
            for (int[] f : faces) {
                sb.append(String.format(Locale.ROOT, "f %d %d %d %d%n", f[0] + base - 1, f[1] + base - 1, f[2] + base - 1, f[3] + base - 1));
            }
            base += 8;
        }
        return sb.toString();
    }

    /** Re-reads an OBJ and checks vertex/face counts match the manifest. */
    public static boolean verify(String obj, BuildManifest m) {
        long v = obj.lines().filter(s -> s.startsWith("v ")).count();
        long f = obj.lines().filter(s -> s.startsWith("f ")).count();
        return v == m.boxes().size() * 8L && f == m.boxes().size() * 6L;
    }
}
