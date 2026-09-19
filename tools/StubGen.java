import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Builds a COMPILE-ONLY stub of the obfuscated Minecraft client jar, renamed to the
 * names a Forge mod sees at runtime: MCP class names with SRG member names
 * (func_xxx / field_xxx). Method bodies are dropped — javac only needs signatures.
 *
 *   java StubGen <client.jar> <joined.srg> <out.jar>
 */
public class StubGen {
    static final Map<String, String> classMap = new HashMap<>();      // notch -> mcp
    static final Map<String, String> fieldMap = new HashMap<>();      // "owner.name" -> srg
    static final Map<String, String> methodMap = new HashMap<>();     // "owner.name desc" -> srg
    static final Map<String, String[]> parents = new HashMap<>();     // notch -> [super, ifaces...]

    public static void main(String[] args) throws Exception {
        readSrg(args[1]);
        List<String> names = new ArrayList<>();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(args[0])) {
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry en = e.nextElement();
                if (!en.getName().endsWith(".class")) continue;
                byte[] b = read(zip.getInputStream(en));
                classes.put(en.getName(), b);
                ClassReader cr = new ClassReader(b);
                List<String> ps = new ArrayList<>();
                if (cr.getSuperName() != null) ps.add(cr.getSuperName());
                ps.addAll(Arrays.asList(cr.getInterfaces()));
                parents.put(cr.getClassName(), ps.toArray(new String[0]));
                names.add(cr.getClassName());
            }
        }
        Remapper remapper = new Remapper() {
            public String map(String type) { return classMap.getOrDefault(type, type); }
            public String mapFieldName(String owner, String name, String desc) {
                String r = resolve(fieldMap, owner, name + "");
                return r != null ? r : name;
            }
            public String mapMethodName(String owner, String name, String desc) {
                if (name.startsWith("<")) return name;
                String r = resolve(methodMap, owner, name + desc);
                return r != null ? r : name;
            }
        };
        int n = 0;
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[2]))) {
            for (Map.Entry<String, byte[]> e : classes.entrySet()) {
                ClassReader cr = new ClassReader(e.getValue());
                ClassWriter cw = new ClassWriter(0);
                cr.accept(new ClassRemapper(cw, remapper), ClassReader.SKIP_CODE);
                String newName = classMap.getOrDefault(cr.getClassName(), cr.getClassName());
                out.putNextEntry(new ZipEntry(newName + ".class"));
                out.write(cw.toByteArray());
                out.closeEntry();
                n++;
            }
        }
        System.out.println("stubbed " + n + " classes");
    }

    /** SRG lists a member on its declaring class only; look up the hierarchy for overrides. */
    static String resolve(Map<String, String> map, String owner, String key) {
        String direct = map.get(owner + "." + key);
        if (direct != null) return direct;
        String[] ps = parents.get(owner);
        if (ps != null) {
            for (String p : ps) {
                String r = resolve(map, p, key);
                if (r != null) return r;
            }
        }
        return null;
    }

    static void readSrg(String path) throws IOException {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 3) continue;
                if (p[0].equals("CL:")) {
                    classMap.put(p[1], p[2]);
                } else if (p[0].equals("FD:")) {
                    int i = p[1].lastIndexOf('/'), j = p[2].lastIndexOf('/');
                    fieldMap.put(p[1].substring(0, i) + "." + p[1].substring(i + 1), p[2].substring(j + 1));
                } else if (p[0].equals("MD:") && p.length >= 5) {
                    int i = p[1].lastIndexOf('/'), j = p[3].lastIndexOf('/');
                    methodMap.put(p[1].substring(0, i) + "." + p[1].substring(i + 1) + p[2], p[3].substring(j + 1));
                }
            }
        }
    }

    static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int k;
        while ((k = in.read(buf)) > 0) bo.write(buf, 0, k);
        return bo.toByteArray();
    }
}
