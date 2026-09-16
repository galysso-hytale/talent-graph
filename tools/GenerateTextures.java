import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
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
 *   <li>{@code Lines/deg_000.png … deg_179.png}: 32×32 tiles with a 2 px white
 *       anti-aliased line through the centre at the given angle (degrees,
 *       screen coordinates, y down). Chained every 24 px they draw a straight
 *       link at any angle; the page tints them per link state.</li>
 *   <li>{@code Node/<State>.png} and {@code Node/<State>_Hovered.png}: 64×64
 *       node backgrounds, one per state. Replace freely, only the size matters.</li>
 *   <li>{@code Icons/Missing.png}: 40×40 fallback icon.</li>
 * </ul>
 */
public final class GenerateTextures {

    static final int TILE = 32;
    static final float LINE_WIDTH = 2f;
    static final int NODE = 64;
    static final int ICON = 40;

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
        node(dir, "Locked", new Color(0x2a, 0x33, 0x40), new Color(0x4a, 0x56, 0x66));
        node(dir, "Available", new Color(0x1f, 0x3a, 0x5c), new Color(0x7a, 0x9c, 0xc6));
        node(dir, "Unlocked", new Color(0x1e, 0x4d, 0x3a), new Color(0x5c, 0xc2, 0x8a));
        node(dir, "Maxed", new Color(0x5a, 0x40, 0x14), new Color(0xe8, 0xa9, 0x3b));
    }

    private static void node(Path dir, String name, Color fill, Color ring) throws IOException {
        ImageIO.write(disc(fill, ring, 3f), "png", dir.resolve(name + ".png").toFile());
        ImageIO.write(disc(fill.brighter(), ring.brighter(), 4f), "png",
                dir.resolve(name + "_Hovered.png").toFile());
    }

    private static BufferedImage disc(Color fill, Color ring, float ringWidth) {
        BufferedImage img = new BufferedImage(NODE, NODE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        int inset = 3;
        g.setColor(fill);
        g.fillOval(inset, inset, NODE - 2 * inset, NODE - 2 * inset);
        g.setColor(ring);
        g.setStroke(new BasicStroke(ringWidth));
        g.drawOval(inset, inset, NODE - 2 * inset, NODE - 2 * inset);
        g.dispose();
        return img;
    }

    private static void icon(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        BufferedImage img = new BufferedImage(ICON, ICON, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        g.setColor(new Color(0xd6, 0xe4, 0xee, 0xb0));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
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
