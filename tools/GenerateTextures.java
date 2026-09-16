import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Generates the placeholder textures of the talent page. Run from the project
 * root with a JDK 25: {@code java tools/GenerateTextures.java}.
 *
 * <p>Output goes to {@code core/src/main/resources/Common/UI/Custom/Pages/TalentGraph/}:</p>
 * <ul>
 *   <li>{@code Lines/deg_000.png … deg_179.png}: 48×48 tiles with a 2 px white
 *       anti-aliased line through the centre at the given angle (degrees,
 *       screen coordinates, y down). Chained every 36 px they draw a straight
 *       link at any angle; the page tints them per link state.</li>
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

    static final int TILE = 48;
    static final float LINE_WIDTH = 2f;
    static final int NODE = 72;
    static final int CORNER = 14;
    static final int BADGE = 24;
    static final int ICON = 64;

    public static void main(String[] args) throws IOException {
        Path root = Path.of("core/src/main/resources/Common/UI/Custom/Pages/TalentGraph");
        lines(root.resolve("Lines"));
        nodes(root.resolve("Node"));
        icon(root.resolve("Icons").resolve("Missing.png"));
        System.out.println("Textures written under " + root);
    }

    private static void lines(Path dir) throws IOException {
        Files.createDirectories(dir);
        // The line must reach the tile edges whatever the angle, so it is
        // drawn longer than the diagonal and clipped by the image bounds.
        double half = TILE;
        double c = TILE / 2.0;
        for (int deg = 0; deg < 180; deg++) {
            double a = Math.toRadians(deg);
            BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = graphics(img);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(LINE_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            g.draw(new Line2D.Double(c - half * Math.cos(a), c - half * Math.sin(a),
                    c + half * Math.cos(a), c + half * Math.sin(a)));
            g.dispose();
            ImageIO.write(img, "png", dir.resolve(String.format("deg_%03d.png", deg)).toFile());
        }
    }

    private static void nodes(Path dir) throws IOException {
        Files.createDirectories(dir);
        Color fill = new Color(0x0d, 0x15, 0x22, 0xf0);
        node(dir, "Locked", fill, new Color(0x4a, 0x56, 0x66));
        node(dir, "Available", fill, new Color(0x7a, 0x9c, 0xc6));
        node(dir, "Unlocked", fill, new Color(0x5c, 0xc2, 0x8a));
        node(dir, "Maxed", fill, new Color(0xe8, 0xa9, 0x3b));
        // Hover feedback of an unlockable node: only the ring, thicker and
        // brighter, drawn over the state frame.
        ImageIO.write(roundedSquare(NODE, CORNER, null, new Color(0xf0, 0xf4, 0xff), 5f),
                "png", dir.resolve("Action_Hovered.png").toFile());
        ImageIO.write(roundedSquare(NODE, CORNER, new Color(0x06, 0x0b, 0x14, 0x99), null, 0f),
                "png", dir.resolve("Dim.png").toFile());
        ImageIO.write(roundedSquare(BADGE, 10, new Color(0x06, 0x0b, 0x14, 0xe6),
                new Color(0xff, 0xff, 0xff, 0x30), 1f), "png", dir.resolve("Badge.png").toFile());
    }

    private static void node(Path dir, String name, Color fill, Color ring) throws IOException {
        ImageIO.write(roundedSquare(NODE, CORNER, fill, ring, 3f), "png",
                dir.resolve(name + ".png").toFile());
    }

    private static BufferedImage roundedSquare(int size, int corner, Color fill, Color ring, float ringWidth) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        float inset = ringWidth / 2f + 0.5f;
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

    private static void icon(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        BufferedImage img = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        g.setColor(new Color(0xd6, 0xe4, 0xee, 0xb0));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
        var metrics = g.getFontMetrics();
        String text = "?";
        g.drawString(text, (ICON - metrics.stringWidth(text)) / 2f,
                (ICON - metrics.getHeight()) / 2f + metrics.getAscent());
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
    }

    private static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }
}
