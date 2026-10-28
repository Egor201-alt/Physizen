package dev.egor201.physizen.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class ObjParser {

    public final float[] vertices;
    public final int[] indices;

    private ObjParser(float[] vertices, int[] indices) {
        this.vertices = vertices;
        this.indices = indices;
    }

    public static ObjParser parse(File file, double scale) throws IOException {
        List<Float> verts = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("v ")) {
                    String[] parts = line.substring(2).trim().split("\\s+");
                    if (parts.length < 3) {
                        continue;
                    }
                    verts.add((float) (Double.parseDouble(parts[0]) * scale));
                    verts.add((float) (Double.parseDouble(parts[1]) * scale));
                    verts.add((float) (Double.parseDouble(parts[2]) * scale));
                }
                else if (line.startsWith("f ")) {
                    String[] parts = line.substring(2).trim().split("\\s+");
                    int[] face = new int[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        String token = parts[i];
                        int slash = token.indexOf("/");
                        if (slash >= 0) {
                            token = token.substring(0, slash);
                        }
                        int value = Integer.parseInt(token);
                        face[i] = value < 0 ? verts.size() / 3 + value : value - 1;
                    }
                    for (int i = 1; i + 1 < face.length; i++) {
                        idx.add(face[0]);
                        idx.add(face[i]);
                        idx.add(face[i + 1]);
                    }
                }
            }
        }
        float[] v = new float[verts.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = verts.get(i);
        }
        int[] f = new int[idx.size()];
        for (int i = 0; i < f.length; i++) {
            f[i] = idx.get(i);
        }
        return new ObjParser(v, f);
    }
}
