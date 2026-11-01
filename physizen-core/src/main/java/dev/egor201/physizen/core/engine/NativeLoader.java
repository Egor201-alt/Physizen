package dev.egor201.physizen.core.engine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public final class NativeLoader {

    private static boolean loaded = false;
    private static String loadedFrom = "none";

    private NativeLoader() {
    }

    public static synchronized String load(File dataFolder, String variant, Logger log) throws IOException {
        if (loaded) {
            return loadedFrom;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        String fileName;
        String osDir;
        if (os.contains("win")) {
            osDir = "windows";
            fileName = "joltjni.dll";
        }
        else if (os.contains("mac") || os.contains("darwin")) {
            osDir = "osx";
            fileName = "libjoltjni.dylib";
        }
        else {
            osDir = "linux";
            fileName = "libjoltjni.so";
        }
        File folder = new File(dataFolder, "natives");
        folder.mkdirs();
        File manual = new File(folder, fileName);
        if (manual.isFile()) {
            System.load(manual.getAbsolutePath());
            loaded = true;
            loadedFrom = manual.getPath();
            return loadedFrom;
        }
        List<String> candidates = new ArrayList<>();
        String cpu = arm ? "aarch64" : "x86-64";
        boolean optimized = wantsOptimized(variant, osDir);
        if (optimized && !arm) {
            if (osDir.equals("linux")) {
                candidates.add("linux/x86-64-fma/com/github/stephengold/" + fileName);
            }
            if (osDir.equals("windows")) {
                candidates.add("windows/x86-64-avx2/com/github/stephengold/" + fileName);
            }
        }
        candidates.add(osDir + "/" + cpu + "/com/github/stephengold/" + fileName);
        if (osDir.equals("windows") && arm) {
            candidates.add("windows/aarch64/com/github/stephengold/libjoltjni.dll");
        }
        ClassLoader loader = NativeLoader.class.getClassLoader();
        for (String candidate : candidates) {
            try (InputStream in = loader.getResourceAsStream(candidate)) {
                if (in == null) {
                    continue;
                }
                File out = new File(folder, "extracted-" + Integer.toHexString(candidate.hashCode()) + "-" + fileName);
                Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.load(out.getAbsolutePath());
                loaded = true;
                loadedFrom = candidate;
                return candidate;
            }
            catch (UnsatisfiedLinkError e) {
                log.warning("Native candidate " + candidate + " failed to load: " + e.getMessage());
            }
        }
        throw new IOException("No Jolt native library found for " + os + "/" + arch + ". Tried: " + candidates + ". Place " + fileName + " into " + folder.getPath() + ".");
    }

    private static boolean wantsOptimized(String variant, String osDir) {
        if (variant == null) {
            return false;
        }
        String v = variant.toLowerCase(Locale.ROOT);
        if (v.equals("baseline")) {
            return false;
        }
        if (v.equals("optimized")) {
            return true;
        }
        if (osDir.equals("linux")) {
            try {
                String info = new String(Files.readAllBytes(new File("/proc/cpuinfo").toPath()));
                return info.contains(" avx2") && info.contains(" fma");
            }
            catch (IOException e) {
                return false;
            }
        }
        return false;
    }
}
