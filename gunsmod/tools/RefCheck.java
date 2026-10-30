import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Lists what compiled mod classes need from Minecraft / NeoForge (every field and method they reference, with its
 * descriptor), and what they declare (methods, super types). Two builds that list the same lines link the same way
 * at runtime.
 *   java RefCheck.java CLASSES_DIR              print the list
 *   java RefCheck.java CLASSES_DIR EXPECTED     print only the differences from an earlier list
 */
public class RefCheck {
    public static void main(String[] args) throws IOException {
        TreeSet<String> lines = new TreeSet<>();
        try (Stream<Path> s = Files.walk(Path.of(args[0]))) {
            for (Path p : s.filter(f -> f.toString().endsWith(".class")).toList()) read(Files.readAllBytes(p), lines);
        }
        if (args.length < 2) {
            lines.forEach(System.out::println);
            return;
        }
        TreeSet<String> expected = new TreeSet<>(Files.readAllLines(Path.of(args[1])));
        int plus = 0, minus = 0;
        for (String l : lines) if (!expected.contains(l)) { System.out.println("REFCHECK + " + l); plus++; }
        for (String l : expected) if (!l.isEmpty() && !lines.contains(l)) { System.out.println("REFCHECK - " + l); minus++; }
        System.out.println("REFCHECK done: " + lines.size() + " lines, +" + plus + " -" + minus);
    }

    private static void read(byte[] bytes, TreeSet<String> out) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        in.readInt();
        in.readUnsignedShort();
        in.readUnsignedShort();
        int n = in.readUnsignedShort();
        int[] tag = new int[n];
        int[] a = new int[n], b = new int[n];
        String[] utf = new String[n];
        for (int i = 1; i < n; i++) {
            tag[i] = in.readUnsignedByte();
            switch (tag[i]) {
                case 1 -> utf[i] = in.readUTF();
                case 3, 4 -> in.readInt();
                case 5, 6 -> { in.readLong(); i++; }
                case 7, 8, 16, 19, 20 -> a[i] = in.readUnsignedShort();
                case 9, 10, 11, 12, 17, 18 -> { a[i] = in.readUnsignedShort(); b[i] = in.readUnsignedShort(); }
                case 15 -> { in.readUnsignedByte(); a[i] = in.readUnsignedShort(); }
                default -> throw new IOException("bad constant tag " + tag[i]);
            }
        }
        in.readUnsignedShort();
        String self = utf[a[in.readUnsignedShort()]];
        int sup = in.readUnsignedShort();
        if (sup != 0) out.add("S " + self + " " + utf[a[sup]]);
        int ni = in.readUnsignedShort();
        for (int i = 0; i < ni; i++) out.add("I " + self + " " + utf[a[in.readUnsignedShort()]]);
        for (int part = 0; part < 2; part++) {
            int count = in.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                in.readUnsignedShort();
                String name = utf[in.readUnsignedShort()], desc = utf[in.readUnsignedShort()];
                if (part == 1) out.add("D " + self + "." + name + ":" + desc);
                int attrs = in.readUnsignedShort();
                for (int k = 0; k < attrs; k++) {
                    in.readUnsignedShort();
                    in.skipBytes(in.readInt());
                }
            }
        }
        List<String> refs = new ArrayList<>();
        for (int i = 1; i < n; i++) {
            if (tag[i] < 9 || tag[i] > 11) continue;
            String owner = utf[a[a[i]]];
            if (owner.startsWith("com/gunsmod/") || owner.startsWith("java/") || owner.startsWith("javax/")) continue;
            String kind = tag[i] == 9 ? "F" : tag[i] == 10 ? "M" : "IM";
            refs.add("R " + kind + " " + owner + "." + utf[a[b[i]]] + ":" + utf[b[b[i]]]);
        }
        out.addAll(refs);
    }
}
