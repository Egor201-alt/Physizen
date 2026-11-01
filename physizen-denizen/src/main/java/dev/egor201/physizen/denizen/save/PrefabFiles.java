package dev.egor201.physizen.denizen.save;

import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.objects.core.ListTag;
import com.denizenscript.denizencore.objects.core.MapTag;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.egor201.physizen.core.PhysException;
import dev.egor201.physizen.core.io.PhysBinary;
import dev.egor201.physizen.denizen.DenizenServices;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PrefabFiles {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PrefabFiles() {
    }

    private static File resolveFile(String fileName, boolean write) {
        if (fileName == null || fileName.trim().isEmpty() || fileName.contains("..") || fileName.startsWith("/") || fileName.startsWith("\\") || fileName.contains(":")) {
            throw new PhysException("Invalid save file name.");
        }
        String n = fileName.trim();
        String lower = n.toLowerCase();
        File folder = DenizenServices.saveFolder();
        if (write) {
            if (!lower.endsWith(".json") && !lower.endsWith(".phys")) {
                n += ".phys";
            }
            File f = new File(folder, n);
            if (f.getParentFile() != null) {
                f.getParentFile().mkdirs();
            }
            return f;
        }
        if (lower.endsWith(".json") || lower.endsWith(".phys")) {
            return new File(folder, n);
        }
        File phys = new File(folder, n + ".phys");
        return phys.isFile() ? phys : new File(folder, n + ".json");
    }

    public static void saveToFile(MapTag root, String fileName) {
        File file = resolveFile(fileName, true);
        try {
            Object unpacked = unpack(root);
            if (file.getName().toLowerCase().endsWith(".json")) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
                    GSON.toJson(unpacked, writer);
                }
            }
            else {
                Files.write(file.toPath(), PhysBinary.encodeFile(unpacked));
            }
        }
        catch (Exception e) {
            throw new PhysException("Failed to save prefab to " + file.getName() + ": " + e.getMessage(), e);
        }
    }

    public static MapTag loadFromFile(String fileName) {
        File file = resolveFile(fileName, false);
        if (!file.isFile()) {
            throw new PhysException("Save file not found: " + file.getName());
        }
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            Object raw;
            if (PhysBinary.isFile(data)) {
                raw = PhysBinary.decodeFile(data);
            }
            else {
                raw = GSON.fromJson(new String(data, StandardCharsets.UTF_8), Object.class);
            }
            return (MapTag) pack(raw);
        }
        catch (Exception e) {
            throw new PhysException("Failed to load prefab from " + file.getName() + ": " + e.getMessage(), e);
        }
    }

    public static List<String> listSavedFiles() {
        File folder = DenizenServices.saveFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json") || name.endsWith(".phys"));
        Set<String> names = new LinkedHashSet<>();
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                names.add(name.substring(0, name.length() - 5));
            }
        }
        return new ArrayList<>(names);
    }

    public static byte[] toBlob(MapTag map) throws IOException {
        return PhysBinary.encodeRaw(unpack(map));
    }

    public static MapTag fromBlob(byte[] data) throws IOException {
        return (MapTag) pack(PhysBinary.decodeRaw(data));
    }

    private static Object unpack(ObjectTag tag) {
        if (tag instanceof MapTag) {
            Map<String, Object> map = new LinkedHashMap<>();
            MapTag mt = (MapTag) tag;
            for (String key : mt.keys()) {
                map.put(key, unpack(mt.getObject(key)));
            }
            return map;
        }
        else if (tag instanceof ListTag) {
            List<Object> list = new ArrayList<>();
            ListTag lt = (ListTag) tag;
            for (ObjectTag obj : lt.objectForms) {
                list.add(unpack(obj));
            }
            return list;
        }
        else {
            return tag.toString();
        }
    }

    @SuppressWarnings("unchecked")
    private static ObjectTag pack(Object obj) {
        if (obj instanceof Map) {
            MapTag map = new MapTag();
            Map<String, Object> m = (Map<String, Object>) obj;
            for (Map.Entry<String, Object> entry : m.entrySet()) {
                map.putObject(entry.getKey(), pack(entry.getValue()));
            }
            return map;
        }
        else if (obj instanceof List) {
            ListTag list = new ListTag();
            List<Object> l = (List<Object>) obj;
            for (Object item : l) {
                list.addObject(pack(item));
            }
            return list;
        }
        else {
            return new ElementTag(String.valueOf(obj));
        }
    }
}
