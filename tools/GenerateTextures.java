import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Generates the placeholder textures of the talent page. Run from the project
 * root with a JDK 25: {@code java tools/GenerateTextures.java}.
 *
 * <p>Every texture is written twice, as {@code Name.png} (1 texel per UI unit)
 * and {@code Name@2x.png} (2 texels per unit), the client's own convention:
 * it picks the variant matching its UI scale, so textures are shown 1:1 on
 * both 1080p and 4K screens and nothing is resampled.</p>
 *
 * <p>Output goes to {@code core/src/main/resources/Common/UI/Custom/Pages/TalentGraph/}:</p>
 * <ul>
 *   <li>{@code Lines/<a>_<b>.png}: one tile per integer direction {@code (a, b)}
 *       (see {@link #directions()}), holding a 2 px white anti-aliased line
 *       through the tile centre at the exact angle {@code atan2(b, a)}
 *       (screen coordinates, y down). Laid at {@code P0 + k·(a, b)} for
 *       consecutive {@code k}, they draw a straight link: each tile keeps
 *       only the pixels whose projection on {@code (a, b)} falls within half a
 *       step of its centre, so the tiles partition the pixels of the line
 *       and the result is identical to a single stroke — no overlap, no
 *       double anti-aliasing. {@code SpriteLinkRenderer} mirrors the
 *       direction set and the tile size rule.</li>
 *   <li>{@code Node/<State>.png}: 72×72 rounded squares, one per state, drawn
 *       as 9-slices with an 8 px border so they survive resizing.
 *       {@code Node/Action_Hovered.png} is the thicker ring shown when hovering
 *       a talent that can be unlocked right now, {@code Node/Dim.png} darkens
 *       the icon of a locked talent, {@code Node/Badge.png} backs the cost and
 *       rank labels.</li>
 *   <li>{@code Icons/Missing.png}: 64×64 fallback icon, the native size of the
 *       game's item icons ({@code Icons/ItemsGenerated/*.png}).</li>
 * </ul>
 */
public final class GenerateTextures {

    /** Directions {@code (a, b)} with {@code max(|a|, |b|)} up to this are all generated… */
    static final int MAX_COMPONENT = 32;
    /** …plus the near-axis ones {@code (a, ±1)} and {@code (1, ±b)} up to this. */
    static final int MAX_AXIS_COMPONENT = 64;
    /** Shortest step between tile centres: a short direction is scaled up to it, to keep tiles per link low. */
    static final int MIN_STEP = 32;
    static final float LINE_WIDTH = 2f;
    static final int NODE = 72;
    static final int CORNER = 14;
    static final int BADGE = 24;
    static final int GEM = 8;
    static final int GEM_LARGE = 12;
    static final int PIP = 8;
    static final int CHECK_WIDTH = 12;
    static final int CHECK_HEIGHT = 10;
    static final int ICON = 64;
    /** Texel-per-unit scales to generate, with the file suffix of each. */
    static final int[] SCALES = {1, 2};

    static String suffix(int scale) {
        return scale == 1 ? "" : "@" + scale + "x";
    }

    public static void main(String[] args) throws IOException {
        Path root = Path.of("core/src/main/resources/Common/UI/Custom/Pages/TalentGraph");
        lines(root.resolve("Lines"));
        nodes(root.resolve("Node"));
        icon(root.resolve("Icons"));
        System.out.println("Textures written under " + root);
    }

    private static void lines(Path dir) throws IOException {
        Files.createDirectories(dir);
        int count = 0;
        for (int[] direction : directions()) {
            int a = direction[0];
            int b = direction[1];
            for (int scale : SCALES) {
                ImageIO.write(lineTile(a, b, scale), "png",
                        dir.resolve(a + "_" + b + suffix(scale) + ".png").toFile());
                count++;
            }
        }
        System.out.println(count + " line tiles");
    }

    /**
     * The tile of direction {@code (a, b)} at {@code scale} texels per unit:
     * the line through the centre, then the ownership mask.
     */
    private static BufferedImage lineTile(int a, int b, int scale) {
        int width = tileSize(a) * scale;
        int height = tileSize(b) * scale;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(LINE_WIDTH * scale, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        // Drawn well past the tile so that its ends never show; the
        // ownership mask below does the actual cutting.
        double cx = width / 2.0;
        double cy = height / 2.0;
        g.draw(new Line2D.Double(cx - 2.0 * a * scale, cy - 2.0 * b * scale,
                cx + 2.0 * a * scale, cy + 2.0 * b * scale));
        g.dispose();
        // Keep texel (x, y) iff the projection of its centre on (a, b),
        // measured from the tile centre, lies in [-s/2, s/2) units with
        // s = |(a, b)|, i.e. [-scale·s/2, scale·s/2) texels. Multiplying
        // through by 2s keeps the test in integers, hence exact: laid a
        // step apart, consecutive tiles then own disjoint, adjacent sets of
        // texels of the same line.
        int limit = scale * (a * a + b * b);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int projection = (2 * x + 1 - width) * a + (2 * y + 1 - height) * b;
                int alpha = projection < -limit || projection >= limit ? 0 : img.getRGB(x, y) >>> 24;
                // White under every texel, transparent ones included, so
                // that any filtering the client applies never blends
                // towards black.
                img.setRGB(x, y, alpha << 24 | 0xffffff);
            }
        }
        return img;
    }

    /**
     * The integer directions with a tile, as {@code {a, b}} pairs: primitive
     * vectors (gcd 1) in canonical orientation ({@code a > 0}, or {@code a == 0}
     * and {@code b > 0}, since a line has no direction) with both components
     * up to {@link #MAX_COMPONENT}, plus {@code (a, ±1)} and {@code (1, ±b)}
     * up to {@link #MAX_AXIS_COMPONENT} where the angular gaps are widest.
     * The nearest direction is then never more than 0.41° off. Each vector is
     * scaled by the smallest integer that brings its length to
     * {@link #MIN_STEP} at least, so steps range from 32 to 64 px.
     */
    static List<int[]> directions() {
        List<int[]> directions = new ArrayList<>();
        for (int a = 0; a <= MAX_AXIS_COMPONENT; a++) {
            for (int b = -MAX_AXIS_COMPONENT; b <= MAX_AXIS_COMPONENT; b++) {
                if (a == 0 && b <= 0 || gcd(a, Math.abs(b)) != 1) {
                    continue;
                }
                boolean nearAxis = a == 1 || Math.abs(b) == 1;
                if (Math.max(a, Math.abs(b)) <= MAX_COMPONENT || nearAxis) {
                    int scale = (int) Math.ceil(MIN_STEP / Math.hypot(a, b));
                    directions.add(new int[] {scale * a, scale * b});
                }
            }
        }
        return directions;
    }

    /** Tile extent along one axis, in units: the step plus room for the line width, even so the centre is a whole unit. */
    static int tileSize(int component) {
        return (Math.abs(component) + 7) / 2 * 2;
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    private static void nodes(Path dir) throws IOException {
        Files.createDirectories(dir);
        // Opaque: the links must not show through the icons.
        Color fill = new Color(0x0d, 0x15, 0x22);
        // Ring colours chosen so that the states stay apart under red-green
        // colour blindness: blue vs yellow is safe, and the yellow (partial)
        // is much brighter than the green (complete) so that luminance alone
        // still separates them.
        for (int scale : SCALES) {
            node(dir, "Locked", fill, new Color(0x4a, 0x56, 0x66), scale);
            node(dir, "Available", fill, new Color(0x6f, 0xa8, 0xdc), scale);
            node(dir, "Unlocked", fill, new Color(0xf2, 0xc9, 0x4c), scale);
            node(dir, "Maxed", fill, new Color(0x3f, 0xa8, 0x6f), scale);
            // Hover feedback of an unlockable node: only the ring, thicker and
            // brighter, drawn over the state frame.
            ImageIO.write(roundedSquare(NODE, CORNER, null, new Color(0xf0, 0xf4, 0xff), 5f, scale),
                    "png", dir.resolve("Action_Hovered" + suffix(scale) + ".png").toFile());
            ImageIO.write(roundedSquare(NODE, CORNER, new Color(0x06, 0x0b, 0x14, 0x99), null, 0f, scale),
                    "png", dir.resolve("Dim" + suffix(scale) + ".png").toFile());
            ImageIO.write(roundedSquare(BADGE, 10, new Color(0x06, 0x0b, 0x14, 0xe6),
                    new Color(0xff, 0xff, 0xff, 0x30), 1f, scale), "png",
                    dir.resolve("Badge" + suffix(scale) + ".png").toFile());
            ImageIO.write(check(scale), "png", dir.resolve("Check" + suffix(scale) + ".png").toFile());
            ImageIO.write(gem(GEM, scale), "png", dir.resolve("Point" + suffix(scale) + ".png").toFile());
            ImageIO.write(gem(GEM_LARGE, scale), "png", dir.resolve("PointLarge" + suffix(scale) + ".png").toFile());
            ImageIO.write(pip(true, scale), "png", dir.resolve("PipOn" + suffix(scale) + ".png").toFile());
            ImageIO.write(pip(false, scale), "png", dir.resolve("PipOff" + suffix(scale) + ".png").toFile());
        }
    }

    /** Talent-point gem (prices, header): a rhombus with a lit upper facet. */
    private static BufferedImage gem(int size, int scale) {
        BufferedImage img = new BufferedImage(size * scale, size * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        g.scale(scale, scale);
        float mid = size / 2f;
        var body = new Path2D.Float();
        body.moveTo(mid, 0.5);
        body.lineTo(size - 0.5, mid);
        body.lineTo(mid, size - 0.5);
        body.lineTo(0.5, mid);
        body.closePath();
        g.setColor(new Color(0xd6, 0xe4, 0xee));
        g.fill(body);
        var facet = new Path2D.Float();
        facet.moveTo(mid, 0.5);
        facet.lineTo(size - 0.5, mid);
        facet.lineTo(0.5, mid);
        facet.closePath();
        g.setColor(new Color(0xff, 0xff, 0xff));
        g.fill(facet);
        g.dispose();
        return img;
    }

    /** Rank pip: filled yellow once acquired, hollow grey otherwise, on the node fill. */
    private static BufferedImage pip(boolean on, int scale) {
        BufferedImage img = new BufferedImage(PIP * scale, PIP * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        g.scale(scale, scale);
        float ringWidth = 1.2f;
        float inset = ringWidth / 2f + 0.5f / scale;
        var disc = new Ellipse2D.Float(inset, inset, PIP - 2 * inset, PIP - 2 * inset);
        g.setColor(on ? new Color(0xf2, 0xc9, 0x4c) : new Color(0x0d, 0x15, 0x22));
        g.fill(disc);
        g.setColor(on ? new Color(0x0d, 0x15, 0x22) : new Color(0x96, 0xa9, 0xbe));
        g.setStroke(new BasicStroke(ringWidth));
        g.draw(disc);
        g.dispose();
        return img;
    }

    /** Check mark shown in the rank badge of a complete talent (the game font has no U+2713). */
    private static BufferedImage check(int scale) {
        BufferedImage img = new BufferedImage(CHECK_WIDTH * scale, CHECK_HEIGHT * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        g.scale(scale, scale);
        var path = new Path2D.Float();
        path.moveTo(1.6, 5.4);
        path.lineTo(4.8, 8.4);
        path.lineTo(10.4, 1.6);
        g.setColor(new Color(0xd6, 0xe4, 0xee));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
        g.dispose();
        return img;
    }

    private static void node(Path dir, String name, Color fill, Color ring, int scale) throws IOException {
        ImageIO.write(roundedSquare(NODE, CORNER, fill, ring, 3f, scale), "png",
                dir.resolve(name + suffix(scale) + ".png").toFile());
    }

    /** A rounded square of {@code size} units, drawn at {@code scale} texels per unit. */
    private static BufferedImage roundedSquare(int size, int corner, Color fill, Color ring, float ringWidth, int scale) {
        int texels = size * scale;
        BufferedImage img = new BufferedImage(texels, texels, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        g.scale(scale, scale);
        float inset = ringWidth / 2f + 0.5f / scale;
        var shape = new RoundRectangle2D.Float(inset, inset, size - 2 * inset, size - 2 * inset, corner, corner);
        if (fill != null) {
            g.setColor(fill);
            g.fill(shape);
        }
        if (ring != null) {
            g.setColor(ring);
            g.setStroke(new BasicStroke(ringWidth));
            g.draw(shape);
        }
        g.dispose();
        return img;
    }

    private static void icon(Path dir) throws IOException {
        Files.createDirectories(dir);
        for (int scale : SCALES) {
            int texels = ICON * scale;
            BufferedImage img = new BufferedImage(texels, texels, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = graphics(img);
            g.scale(scale, scale);
            g.setColor(new Color(0xd6, 0xe4, 0xee, 0xb0));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
            var metrics = g.getFontMetrics();
            String text = "?";
            g.drawString(text, (ICON - metrics.stringWidth(text)) / 2f,
                    (ICON - metrics.getHeight()) / 2f + metrics.getAscent());
            g.dispose();
            ImageIO.write(img, "png", dir.resolve("Missing" + suffix(scale) + ".png").toFile());
        }
    }

    private static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }
}
