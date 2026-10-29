package dev.egor201.physizen;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class PhysBinary {

    public static final byte[] MAGIC = {'P', 'H', 'Y', 'S'};
    public static final int VERSION = 1;

    private static final int NULL = 0;
    private static final int MAP = 1;
    private static final int LIST = 2;
    private static final int STRING = 3;
    private static final int INT = 4;
    private static final int DOUBLE = 5;
    private static final int TRUE = 6;
    private static final int FALSE = 7;
    private static final int VEC3 = 8;

    private PhysBinary() {
    }

    public static boolean isFile(byte[] data) {
        return data.length > 5 && data[0] == MAGIC[0] && data[1] == MAGIC[1] && data[2] == MAGIC[2] && data[3] == MAGIC[3];
    }

    public static byte[] encodeFile(Object root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(MAGIC);
        bytes.write(VERSION);
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(payload(root));
        }
        return bytes.toByteArray();
    }

    public static Object decodeFile(byte[] data) throws IOException {
        if (!isFile(data)) {
            throw new IOException("not a .phys file");
        }
        int version = data[4] & 0xFF;
        if (version > VERSION) {
            throw new IOException("file was saved by a newer Physizen (format v" + version + ")");
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data, 5, data.length - 5))) {
            return readPayload(new DataInputStream(gzip));
        }
    }

    public static byte[] encodeRaw(Object root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(VERSION);
        bytes.write(payload(root));
        return bytes.toByteArray();
    }

    public static Object decodeRaw(byte[] data) throws IOException {
        if (data.length < 2 || (data[0] & 0xFF) > VERSION) {
            throw new IOException("unsupported blob");
        }
        return readPayload(new DataInputStream(new ByteArrayInputStream(data, 1, data.length - 1)));
    }

    private static byte[] payload(Object root) throws IOException {
        Map<String, Integer> table = new LinkedHashMap<>();
        collect(root, table);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writeVar(out, table.size());
        for (String s : table.keySet()) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            writeVar(out, b.length);
            out.write(b);
        }
        writeValue(out, root, table);
        out.flush();
        return bytes.toByteArray();
    }

    private static void collect(Object value, Map<String, Integer> table) {
        if (value instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                intern(table, String.valueOf(e.getKey()));
                collect(e.getValue(), table);
            }
        }
        else if (value instanceof List) {
            for (Object o : (List<?>) value) {
                collect(o, table);
            }
        }
        else if (value != null) {
            intern(table, String.valueOf(value));
        }
    }

    private static void intern(Map<String, Integer> table, String s) {
        if (!table.containsKey(s)) {
            table.put(s, table.size());
        }
    }

    private static boolean exactDouble(String s) {
        try {
            return Double.toString(Double.parseDouble(s)).equals(s);
        }
        catch (NumberFormatException e) {
            return false;
        }
    }

    private static void writeValue(DataOutputStream out, Object value, Map<String, Integer> table) throws IOException {
        if (value == null) {
            out.writeByte(NULL);
        }
        else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            out.writeByte(MAP);
            writeVar(out, map.size());
            for (Map.Entry<?, ?> e : map.entrySet()) {
                writeVar(out, table.get(String.valueOf(e.getKey())));
                writeValue(out, e.getValue(), table);
            }
        }
        else if (value instanceof List) {
            List<?> list = (List<?>) value;
            out.writeByte(LIST);
            writeVar(out, list.size());
            for (Object o : list) {
                writeValue(out, o, table);
            }
        }
        else {
            writeScalar(out, String.valueOf(value), table);
        }
    }

    private static void writeScalar(DataOutputStream out, String s, Map<String, Integer> table) throws IOException {
        if (s.equals("true")) {
            out.writeByte(TRUE);
            return;
        }
        if (s.equals("false")) {
            out.writeByte(FALSE);
            return;
        }
        if (s.length() < 20 && !s.isEmpty()) {
            try {
                long l = Long.parseLong(s);
                if (Long.toString(l).equals(s)) {
                    out.writeByte(INT);
                    writeVar(out, (l << 1) ^ (l >> 63));
                    return;
                }
            }
            catch (NumberFormatException ignored) {
            }
        }
        if (exactDouble(s)) {
            out.writeByte(DOUBLE);
            out.writeDouble(Double.parseDouble(s));
            return;
        }
        String[] parts = s.split(",", -1);
        if (parts.length == 3 && exactDouble(parts[0]) && exactDouble(parts[1]) && exactDouble(parts[2])) {
            out.writeByte(VEC3);
            out.writeDouble(Double.parseDouble(parts[0]));
            out.writeDouble(Double.parseDouble(parts[1]));
            out.writeDouble(Double.parseDouble(parts[2]));
            return;
        }
        out.writeByte(STRING);
        writeVar(out, table.get(s));
    }

    private static void writeVar(DataOutputStream out, long value) throws IOException {
        while ((value & ~0x7FL) != 0) {
            out.writeByte((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.writeByte((int) value);
    }

    private static long readVar(DataInputStream in) throws IOException {
        long result = 0;
        int shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift > 63) {
                throw new IOException("bad varint");
            }
        }
    }

    private static int readCount(DataInputStream in) throws IOException {
        long n = readVar(in);
        if (n < 0 || n > 10_000_000) {
            throw new IOException("invalid count " + n);
        }
        return (int) n;
    }

    private static Object readPayload(DataInputStream in) throws IOException {
        int count = readCount(in);
        List<String> table = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int len = readCount(in);
            byte[] b = new byte[len];
            in.readFully(b);
            table.add(new String(b, StandardCharsets.UTF_8));
        }
        return readValue(in, table);
    }

    private static String ref(DataInputStream in, List<String> table) throws IOException {
        int idx = readCount(in);
        if (idx >= table.size()) {
            throw new IOException("bad string reference");
        }
        return table.get(idx);
    }

    private static Object readValue(DataInputStream in, List<String> table) throws IOException {
        int tag = in.readUnsignedByte();
        switch (tag) {
            case NULL:
                return null;
            case MAP: {
                int n = readCount(in);
                Map<String, Object> map = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) {
                    String key = ref(in, table);
                    map.put(key, readValue(in, table));
                }
                return map;
            }
            case LIST: {
                int n = readCount(in);
                List<Object> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    list.add(readValue(in, table));
                }
                return list;
            }
            case STRING:
                return ref(in, table);
            case INT: {
                long z = readVar(in);
                return Long.toString((z >>> 1) ^ -(z & 1));
            }
            case DOUBLE:
                return Double.toString(in.readDouble());
            case TRUE:
                return "true";
            case FALSE:
                return "false";
            case VEC3:
                return Double.toString(in.readDouble()) + "," + Double.toString(in.readDouble()) + "," + Double.toString(in.readDouble());
            default:
                throw new IOException("unknown tag " + tag);
        }
    }
}