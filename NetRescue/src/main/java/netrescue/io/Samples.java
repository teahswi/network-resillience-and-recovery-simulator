package netrescue.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import netrescue.graph.Graph;

/** Bundled example networks (stored as resources inside the jar). */
public final class Samples {
    private Samples() {}

    public static final String[][] LIST = {
        {"classroom", "Classroom demo (10 routers, has critical points)", "classroom.txt"},
        {"india", "India backbone (sample, 20 cities)", "india.txt"},
        {"abilene", "Abilene / Internet2 USA (GML, 11 routers)", "abilene.gml"},
        {"mesh", "Campus mesh (16 routers, highly redundant)", "mesh.txt"},
    };

    public static Graph load(String key) {
        for (String[] s : LIST) {
            if (!s[0].equals(key)) continue;
            String text = read("/samples/" + s[2]);
            return s[2].endsWith(".gml") ? GmlParser.parse(text) : TextFormat.parse(text);
        }
        throw new IllegalArgumentException("unknown sample '" + key + "'");
    }

    public static String read(String resource) {
        try (InputStream in = Samples.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalArgumentException("missing resource " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + resource, e);
        }
    }
}
