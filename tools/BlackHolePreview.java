import com.hhy.dreamingfishcore.gameplay.kill_effect_system.client.BlackHoleGeometry;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Exports the production mesh (not a reimplementation) for offline visual inspection. */
public final class BlackHolePreview {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);
        float pitch = args.length > 1 ? Float.parseFloat(args[1]) : 27;
        float s = (float) Math.sin(Math.toRadians(pitch));
        float c = (float) Math.cos(Math.toRadians(pitch));
        var view = new BlackHoleGeometry.View(new BlackHoleGeometry.Point(1, 0, 0),
                new BlackHoleGeometry.Point(0, c, -s), new BlackHoleGeometry.Point(0, s, c));
        float[] phases = {0.10F, 0.32F, 0.61F, 0.705F, 0.77F, 0.87F};
        try (var output = new DataOutputStream(Files.newOutputStream(directory.resolve("mesh.bin")))) {
            output.writeInt(phases.length);
            output.writeFloat(s);
            output.writeFloat(c);
            for (float progress : phases) {
                float fall = Math.max(0, Math.min(1, (progress - 0.16F) / 0.34F));
                var frame = BlackHoleGeometry.sample(0.6F, 1.95F, progress * 36, 36,
                        1.95F - (1.95F * 1.2F + 0.25F) * fall * fall, 1729, false);
                output.writeFloat(progress);
                for (var pass : BlackHoleGeometry.Pass.values()) {
                    ArrayList<Float> vertices = new ArrayList<>();
                    BlackHoleGeometry.render(pass, frame, view, (x, y, z, r, g, b, a) -> {
                        vertices.add(x); vertices.add(y); vertices.add(z);
                        vertices.add(r); vertices.add(g); vertices.add(b); vertices.add(a);
                    });
                    output.writeInt(vertices.size());
                    for (float vertex : vertices) output.writeFloat(vertex);
                }
            }
        }
    }
}
