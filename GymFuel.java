import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * GymFuel: tap a muscle, see what to eat for it.
 * Pure Java (Swing + Java2D). No libraries. Run:  javac GymFuel.java && java GymFuel
 *
 * UI animation and micro-interactions in this file:
 *  - muscles ease into a hover colour, grow slightly, and the selected one pulses with a glow
 *  - front/back switch does a 3D-style flip of the body
 *  - sliding pill on the Front/Back control and on the goal chips
 *  - food cards slide and fade in one after another, macro bars fill up
 *  - cards lift on hover; the Add button turns into a check mark that draws itself
 *  - ripples on buttons, a shockwave ring where you click a muscle
 *  - plate totals count up, protein progress bar glides
 *  - tooltip fades in next to the cursor
 */
public class GymFuel extends JPanel implements MouseMotionListener, MouseListener, KeyListener {

    // ===================================================================== data
    static final class Food {
        final String name, serving, why;
        final int kcal, p, c, f;
        final Color tint;
        Food(String name, String serving, int kcal, int p, int c, int f, int tint, String why) {
            this.name = name; this.serving = serving; this.kcal = kcal;
            this.p = p; this.c = c; this.f = f; this.tint = new Color(tint); this.why = why;
        }
    }

    static final class MuscleInfo {
        final String key, label, blurb; final List<Food> foods;
        MuscleInfo(String key, String label, String blurb, Food... foods) {
            this.key = key; this.label = label; this.blurb = blurb; this.foods = Arrays.asList(foods);
        }
    }

    static final class Zone {
        final String key; final boolean front; final List<Shape> shapes;
        Zone(String key, boolean front, List<Shape> shapes) { this.key = key; this.front = front; this.shapes = shapes; }
    }

    static final class Ripple {
        final double x, y; final Shape clip; final Color color; double t = 0;
        Ripple(double x, double y, Shape clip, Color color) { this.x = x; this.y = y; this.clip = clip; this.color = color; }
    }

    // approximate values per serving
    static final Food CHICKEN  = new Food("Chicken breast", "150 g, grilled", 248, 46, 0, 5, 0xFF8A5B, "Lean complete protein to repair worked fibres.");
    static final Food EGGS     = new Food("Whole eggs", "3 large", 215, 19, 1, 14, 0xFFD166, "Protein plus healthy fats and choline.");
    static final Food OATS     = new Food("Oats", "80 g, dry", 300, 10, 54, 6, 0xE9C46A, "Slow carbs to fuel heavy pressing sessions.");
    static final Food SPINACH  = new Food("Spinach", "100 g, steamed", 23, 3, 4, 0, 0x52D68A, "Iron and magnesium, almost no calories.");
    static final Food YOGHURT  = new Food("Greek yoghurt", "200 g, plain", 120, 20, 8, 1, 0x7AB8FF, "Protein and calcium, an easy post-workout snack.");
    static final Food BILTONG  = new Food("Biltong", "50 g", 130, 24, 1, 3, 0xC1666B, "Dense, portable protein. Watch the salt.");
    static final Food BANANA   = new Food("Banana", "1 medium", 105, 1, 27, 0, 0xFFE066, "Quick carbs and potassium.");
    static final Food COTTAGE  = new Food("Cottage cheese", "200 g", 196, 22, 7, 9, 0xBFD7FF, "Slow-digesting casein protein.");
    static final Food BROCCOLI = new Food("Broccoli", "150 g, steamed", 51, 4, 10, 1, 0x4ADE80, "Fibre and vitamin C, keeps meals filling.");
    static final Food TUNA     = new Food("Tuna", "120 g, drained", 130, 28, 0, 1, 0x5EB1FF, "Very lean protein with almost no fat.");
    static final Food SWEET    = new Food("Sweet potato", "200 g, baked", 172, 3, 40, 0, 0xFF9F4A, "Complex carbs to refill muscle glycogen.");
    static final Food BEEF     = new Food("Lean beef mince", "150 g, cooked", 230, 33, 0, 11, 0xE5567B, "Protein plus iron and zinc.");
    static final Food PAP      = new Food("Pap", "200 g, cooked", 120, 3, 26, 1, 0xF4D58D, "Affordable South African staple for energy.");
    static final Food BEET     = new Food("Beetroot", "150 g, boiled", 65, 2, 15, 0, 0xD94F8C, "Natural nitrates that support blood flow.");
    static final Food MAAS     = new Food("Maas (amasi)", "250 ml", 150, 8, 10, 8, 0xDDE7F5, "Fermented dairy: protein and probiotics.");
    static final Food SALMON   = new Food("Salmon", "150 g, baked", 310, 34, 0, 18, 0xFF7F6B, "Omega-3 fats help recovery and joints.");
    static final Food ALMONDS  = new Food("Almonds", "30 g", 175, 6, 6, 15, 0xD9A66B, "Healthy fats and vitamin E.");
    static final Food LENTILS  = new Food("Lentils", "200 g, cooked", 230, 18, 40, 1, 0xB5835A, "Plant protein with plenty of fibre.");
    static final Food PEANUT   = new Food("Peanut butter", "2 tbsp", 190, 8, 6, 16, 0xD08B3F, "Calorie-dense fats and protein.");
    static final Food MILK     = new Food("Milk", "250 ml", 150, 8, 12, 8, 0xEAF2FF, "Protein, calcium and carbs in one glass.");
    static final Food RICE     = new Food("Brown rice", "200 g, cooked", 222, 5, 46, 2, 0xCDB892, "Steady carbs for long training days.");
    static final Food AVOCADO  = new Food("Avocado", "half", 160, 2, 9, 15, 0x7BC96F, "Healthy fats and potassium.");

    static final Map<String, MuscleInfo> MUSCLES = new LinkedHashMap<>();
    static void m(String key, String label, String blurb, Food... f) { MUSCLES.put(key, new MuscleInfo(key, label, blurb, f)); }
    static {
        m("shoulders", "Shoulders", "Deltoids drive every overhead press and lateral raise. Fuel them with carbs and protein.", OATS, EGGS, SPINACH);
        m("chest", "Chest", "The pecs power every pressing movement. Prioritise protein and carbs around bench day.", CHICKEN, RICE, EGGS);
        m("biceps", "Biceps", "Small but quick to recover. Steady protein through the day keeps your curls progressing.", YOGHURT, BILTONG, BANANA);
        m("abs", "Abs", "Visible abs come from training plus your overall diet. Lean protein and fibre keep you full.", COTTAGE, BROCCOLI, TUNA);
        m("quads", "Quads", "Big muscles that burn a lot of fuel on squat and leg days. Do not skimp on carbs.", SWEET, BEEF, PAP);
        m("calves", "Calves", "Dense, hard-working muscles. Potassium-rich foods support steady training.", BANANA, BEET, MAAS);
        m("traps", "Traps", "Upper back and neck stabilisers, worked by shrugs and deadlifts.", SALMON, ALMONDS, SPINACH);
        m("lats", "Lats", "The wide back muscles behind pull-ups and rows. Protein and slow carbs help them grow.", CHICKEN, LENTILS, OATS);
        m("triceps", "Triceps", "Two thirds of your upper arm. Protein and healthy fats support pressing days.", EGGS, PEANUT, BROCCOLI);
        m("lowerback", "Lower back", "It supports every heavy lift. Omega-3 fats and carbs help it recover.", SALMON, SWEET, MILK);
        m("glutes", "Glutes", "The biggest muscles in the body. Protein and carbs fuel hip thrusts and squats.", BEEF, RICE, AVOCADO);
        m("hamstrings", "Hamstrings", "The back of the thigh, trained by deadlifts and leg curls. Lean protein and carbs refill them.", TUNA, RICE, BEET);
    }

    // body shapes in a 300 x 530 logical space, centre line x = 150
    static final List<Zone> ZONES = new ArrayList<>();
    static Shape mir(Shape s) { return new AffineTransform(-1, 0, 0, 1, 300, 0).createTransformedShape(s); }
    static void zone(String key, boolean front, Shape... s) { ZONES.add(new Zone(key, front, Arrays.asList(s))); }
    static RoundRectangle2D.Double rr(double x, double y, double w, double h, double r) { return new RoundRectangle2D.Double(x, y, w, h, r, r); }
    static RoundRectangle2D.Double rr(double x, double y, double w, double h, double aw, double ah) { return new RoundRectangle2D.Double(x, y, w, h, aw, ah); }
    static {
        // front
        Shape sh = new Ellipse2D.Double(70, 122, 42, 44);
        zone("shoulders", true, sh, mir(sh));
        Shape ch = rr(108, 126, 40, 42, 20, 20);
        zone("chest", true, ch, mir(ch));
        Shape bi = rr(64, 172, 24, 58, 14, 14);
        zone("biceps", true, bi, mir(bi));
        List<Shape> abs = new ArrayList<>();
        for (int r = 0; r < 3; r++) for (int c = 0; c < 2; c++) abs.add(rr(116 + c * 38, 176 + r * 26, 30, 22, 10, 10));
        ZONES.add(new Zone("abs", true, abs));
        Shape q = rr(104, 296, 42, 112, 22, 22);
        zone("quads", true, q, mir(q));
        Shape cf = rr(110, 424, 30, 84, 16, 16);
        zone("calves", true, cf, mir(cf));
        // back
        zone("shoulders", false, sh, mir(sh));
        Path2D.Double trap = new Path2D.Double();
        trap.moveTo(132, 100); trap.lineTo(168, 100); trap.lineTo(194, 130); trap.lineTo(150, 156); trap.lineTo(106, 130); trap.closePath();
        zone("traps", false, trap);
        Path2D.Double lat = new Path2D.Double();
        lat.moveTo(106, 140); lat.lineTo(144, 164); lat.lineTo(142, 222); lat.lineTo(114, 212); lat.closePath();
        zone("lats", false, lat, mir(lat));
        zone("triceps", false, bi, mir(bi));
        zone("lowerback", false, rr(126, 226, 48, 44, 16, 16));
        Shape gl = new Ellipse2D.Double(106, 276, 44, 40);
        zone("glutes", false, gl, mir(gl));
        Shape ha = rr(104, 322, 42, 90, 22, 22);
        zone("hamstrings", false, ha, mir(ha));
        zone("calves", false, cf, mir(cf));
    }

    // ===================================================================== look
    static final int W = 1120, H = 740;
    static final int BODY_X = 95, BODY_Y = 150;       // body origin on screen
    static final int RX = 500, RW = 590;              // right column
    static final Color BG1 = new Color(0x0C1120), BG2 = new Color(0x151D31);
    static final Color PANEL = new Color(0x171F33), CARD = new Color(0x1D273D), CARD_HI = new Color(0x26324C);
    static final Color LINE = new Color(0x2C3955), TEXT = new Color(0xEBF0F9), MUTED = new Color(0x8E9AB5);
    static final Color ACCENT = new Color(0xFF6B3D), ACCENT2 = new Color(0xFFB347), GREEN = new Color(0x34D399);
    static final Color BASE = new Color(0x263049), BASE_HI = new Color(0x364462), DARK = new Color(0x10131C);
    static final Color PCOL = new Color(0xFF6B3D), CCOL = new Color(0xFFC24B), FCOL = new Color(0x5BC0FF);

    static final Font F_LOGO = new Font("SansSerif", Font.BOLD, 30);
    static final Font F_H1 = new Font("SansSerif", Font.BOLD, 30);
    static final Font F_H2 = new Font("SansSerif", Font.BOLD, 18);
    static final Font F_BODY = new Font("SansSerif", Font.PLAIN, 14);
    static final Font F_SMALL = new Font("SansSerif", Font.PLAIN, 12);
    static final Font F_TINY = new Font("SansSerif", Font.PLAIN, 11);
    static final Font F_BOLD = new Font("SansSerif", Font.BOLD, 14);
    static final Font F_BIG = new Font("SansSerif", Font.BOLD, 24);
    static final Font F_CAPS = new Font("SansSerif", Font.BOLD, 11).deriveFont(Map.of(TextAttribute.TRACKING, 0.16f));

    // ===================================================================== state
    private final Map<String, Double> hov = new HashMap<>(), sel = new HashMap<>();
    private String hoverKey = null, selKey = "chest";
    private boolean wantFront = true, shownFront = true;
    private double flipP = 1, segX = 0, goalX = 0, panelT = 0, time = 0, tipA = 0;
    private int goal = 0;
    private final double[] gramsPerKg = {2.0, 1.6, 2.2};   // daily protein per kg of body weight: Bulk, Maintain, Cut
    private int weightKg = 75;                              // body weight, change with the - and + buttons or Up/Down keys
    private String holdBtn = null; private double holdT = 0, holdAcc = 0;   // hold a button to repeat
    int proteinTarget() { return (int) Math.round(weightKg * gramsPerKg[goal]); }
    void changeWeight(int d) { weightKg = Math.max(30, Math.min(200, weightKg + d)); }
    private final String[] goalName = {"Bulk", "Maintain", "Cut"};
    private final double[] cardHov = new double[3];
    private final Map<Food, Double> added = new HashMap<>();
    private final LinkedHashSet<Food> plate = new LinkedHashSet<>();
    private double dK, dP, dC, dF, dBar;
    private int mx = -1, my = -1, hoverCard = -1;
    private String hoverBtn = null;
    private final List<Ripple> ripples = new ArrayList<>();
    private long last = System.nanoTime();

    public GymFuel() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        addMouseListener(this); addMouseMotionListener(this); addKeyListener(this);
        new javax.swing.Timer(16, e -> { tick(); repaint(); }).start();
    }

    // ===================================================================== update
    static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    static double ease(double t) { return 1 - Math.pow(1 - clamp(t), 3); }
    static double ap(double cur, double target, double dt, double speed) { return cur + (target - cur) * (1 - Math.exp(-speed * dt)); }
    static Color lerp(Color a, Color b, double t) {
        t = clamp(t);
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
    static Color al(Color c, double a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(255 * clamp(a))); }

    MuscleInfo info() { return MUSCLES.get(selKey); }
    double flipScale() { return flipP >= 1 ? 1 : Math.abs(Math.cos(Math.PI * flipP)); }

    void tick() {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - last) / 1e9);
        last = now;
        step(dt);
    }

    void step(double dt) {
        time += dt; panelT += dt;
        if (flipP < 1) { flipP = Math.min(1, flipP + dt / 0.5); if (flipP >= 0.5) shownFront = wantFront; }
        segX = ap(segX, wantFront ? 0 : 1, dt, 14);
        goalX = ap(goalX, goal, dt, 14);
        for (String k : MUSCLES.keySet()) {
            hov.put(k, ap(hov.getOrDefault(k, 0.0), k.equals(hoverKey) ? 1 : 0, dt, 16));
            sel.put(k, ap(sel.getOrDefault(k, 0.0), k.equals(selKey) ? 1 : 0, dt, 10));
        }
        for (int i = 0; i < 3; i++) cardHov[i] = ap(cardHov[i], i == hoverCard ? 1 : 0, dt, 14);
        for (MuscleInfo mi : MUSCLES.values()) for (Food f : mi.foods)
            added.put(f, ap(added.getOrDefault(f, 0.0), plate.contains(f) ? 1 : 0, dt, 9));
        tipA = ap(tipA, hoverKey != null && flipP >= 1 ? 1 : 0, dt, 18);
        int k = 0, p = 0, c = 0, f = 0;
        for (Food x : plate) { k += x.kcal; p += x.p; c += x.c; f += x.f; }
        dK = ap(dK, k, dt, 9); dP = ap(dP, p, dt, 9); dC = ap(dC, c, dt, 9); dF = ap(dF, f, dt, 9);
        dBar = ap(dBar, Math.min(1, p / (double) proteinTarget()), dt, 7);
        if (holdBtn != null) {
            holdT += dt;
            if (holdT > 0.4) { holdAcc += dt; while (holdAcc > 0.06) { holdAcc -= 0.06; changeWeight(holdBtn.equals("wplus") ? 1 : -1); } }
        }
        for (Iterator<Ripple> it = ripples.iterator(); it.hasNext(); ) { Ripple r = it.next(); r.t += dt; if (r.t > 0.7) it.remove(); }
    }

    // ===================================================================== layout
    static Rectangle2D.Double segRect() { return new Rectangle2D.Double(40, 96, 200, 38); }
    static Rectangle2D.Double weightBox() { return new Rectangle2D.Double(430, 24, 250, 34); }
    static Rectangle2D.Double wMinusRect() { return new Rectangle2D.Double(534, 28, 26, 26); }
    static Rectangle2D.Double wPlusRect() { return new Rectangle2D.Double(642, 28, 26, 26); }
    static Rectangle2D.Double goalBox() { return new Rectangle2D.Double(W - 30 - 3 * 96 - 2 * 10 - 4, 20, 3 * 96 + 2 * 10 + 8, 42); }
    static Rectangle2D.Double goalRect(int i) { return new Rectangle2D.Double(W - 30 - 3 * 96 - 2 * 10 + i * 106, 24, 96, 34); }
    static Rectangle2D.Double cardRect(int i) { return new Rectangle2D.Double(RX, 196 + i * 128, RW, 116); }
    static Rectangle2D.Double addRect(int i) { Rectangle2D r = cardRect(i); return new Rectangle2D.Double(r.getX() + RW - 112, r.getY() + 78, 92, 28); }
    static Rectangle2D.Double clearRect() { return new Rectangle2D.Double(RX + RW - 90, 600, 70, 26); }

    // ===================================================================== painting
    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setPaint(new GradientPaint(0, 0, BG1, W, H, BG2));
        g.fillRect(0, 0, W, H);
        drawHeader(g);
        drawStage(g);
        drawRight(g);
        drawPlate(g);
        drawRipples(g);
        text(g, "General nutrition guidance, not medical advice. Values are approximate.", RX, 730, F_TINY, MUTED);
        g.dispose();
    }

    // ---- small helpers
    static void text(Graphics2D g, String s, double x, double y, Font f, Color c) { g.setFont(f); g.setColor(c); g.drawString(s, (float) x, (float) y); }
    static void rtext(Graphics2D g, String s, double xr, double y, Font f, Color c) { g.setFont(f); int w = g.getFontMetrics().stringWidth(s); text(g, s, xr - w, y, f, c); }
    static void ctext(Graphics2D g, String s, Rectangle2D r, Font f, Color c) {
        g.setFont(f); FontMetrics fm = g.getFontMetrics();
        text(g, s, r.getX() + (r.getWidth() - fm.stringWidth(s)) / 2, r.getY() + (r.getHeight() - fm.getHeight()) / 2 + fm.getAscent(), f, c);
    }
    static String fit(String s, FontMetrics fm, int w) {
        if (fm.stringWidth(s) <= w) return s;
        while (s.length() > 1 && fm.stringWidth(s + "...") > w) s = s.substring(0, s.length() - 1);
        return s + "...";
    }
    static List<String> wrap(String s, FontMetrics fm, int w) {
        List<String> lines = new ArrayList<>(); StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String t = cur.length() == 0 ? word : cur + " " + word;
            if (fm.stringWidth(t) > w && cur.length() > 0) { lines.add(cur.toString()); cur = new StringBuilder(word); } else { cur = new StringBuilder(t); }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }
    static void shadow(Graphics2D g, Shape s, int spread, double alpha) {
        for (int i = spread; i >= 1; i--) {
            g.setColor(new Color(0, 0, 0, (int) (alpha * 255 / spread)));
            g.setStroke(new BasicStroke(i * 2f)); g.draw(s);
        }
    }

    // ---- header
    void drawHeader(Graphics2D g) {
        Ellipse2D dot = new Ellipse2D.Double(30, 24, 34, 34);
        g.setPaint(new GradientPaint(30, 24, ACCENT2, 64, 58, ACCENT)); g.fill(dot);
        g.setColor(DARK); g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(40, 41, 54, 41)); g.fill(rr(38, 35, 4, 12, 2)); g.fill(rr(52, 35, 4, 12, 2));
        text(g, "GymFuel", 76, 50, F_LOGO, TEXT);
        text(g, "Tap a muscle. See what to eat.", 76, 68, F_SMALL, MUTED);

        // body weight stepper (sets the protein target)
        Rectangle2D.Double wb = weightBox();
        g.setColor(PANEL); g.fill(rr(wb.x, wb.y - 4, wb.width, wb.height + 8, 42));
        text(g, "Body weight", wb.x + 18, wb.y + 22, F_SMALL, MUTED);
        for (int i = 0; i < 2; i++) {
            Rectangle2D.Double br = i == 0 ? wMinusRect() : wPlusRect();
            boolean hv = (i == 0 ? "wminus" : "wplus").equals(hoverBtn);
            g.setColor(hv ? lerp(new Color(0x2A3650), ACCENT, 0.5) : new Color(0x2A3650)); g.fill(new Ellipse2D.Double(br.x, br.y, br.width, br.height));
            g.setColor(hv ? DARK : TEXT); g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double cx = br.getCenterX(), cy = br.getCenterY();
            g.draw(new Line2D.Double(cx - 5, cy, cx + 5, cy));
            if (i == 1) g.draw(new Line2D.Double(cx, cy - 5, cx, cy + 5));
        }
        ctext(g, weightKg + " kg", new Rectangle2D.Double(wMinusRect().x + 26, 24, wPlusRect().x - wMinusRect().x - 26, 34), F_BOLD, TEXT);

        // goal chips with sliding pill
        g.setColor(PANEL); g.fill(rr(goalBox().x, goalBox().y, goalBox().width, goalBox().height, 42));
        double px = goalRect(0).x + goalX * 106;
        RoundRectangle2D pill = rr(px, 24, 96, 34, 34);
        g.setPaint(new GradientPaint((float) px, 24, ACCENT2, (float) px + 96, 58, ACCENT)); g.fill(pill);
        for (int i = 0; i < 3; i++) {
            Color c = lerp(MUTED, DARK, 1 - Math.min(1, Math.abs(goalX - i)));
            if (("goal" + i).equals(hoverBtn)) c = lerp(c, TEXT, 0.5);
            ctext(g, goalName[i], goalRect(i), F_BOLD, c);
        }
    }

    // ---- left stage with body
    void drawStage(Graphics2D g) {
        RoundRectangle2D stage = rr(20, 84, 450, 626, 30, 30);
        g.setColor(PANEL); g.fill(stage);
        Shape oldClip = g.getClip(); g.clip(stage);
        double pulse = 0.5 + 0.5 * Math.sin(time * 3.2);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(245, 390), 300, new float[]{0f, 1f},
                new Color[]{al(ACCENT, 0.13 + 0.05 * pulse), al(ACCENT, 0)}));
        g.fillRect(20, 84, 450, 626);
        g.setClip(oldClip);

        // Front / Back control
        Rectangle2D.Double seg = segRect();
        g.setColor(BG1); g.fill(rr(seg.x, seg.y, seg.width, seg.height, 38));
        double sx = seg.x + 3 + segX * 97;
        g.setPaint(new GradientPaint((float) sx, 96, ACCENT2, (float) sx + 97, 134, ACCENT));
        g.fill(rr(sx, seg.y + 3, 97, seg.height - 6, 32));
        String[] segName = {"Front", "Back"};
        for (int i = 0; i < 2; i++) {
            Color c = lerp(MUTED, DARK, 1 - Math.min(1, Math.abs(segX - i)));
            if (("seg" + i).equals(hoverBtn)) c = lerp(c, TEXT, 0.5);
            ctext(g, segName[i], new Rectangle2D.Double(seg.x + i * 100, seg.y, 100, seg.height), F_BOLD, c);
        }
        text(g, "Click a muscle", 262, 120, F_SMALL, MUTED);
        ctext(g, "Press F to flip", new Rectangle2D.Double(20, 684, 450, 18), F_TINY, MUTED);

        // body with flip
        Graphics2D b = (Graphics2D) g.create();
        b.translate(BODY_X + 150, BODY_Y);
        b.scale(flipScale(), 1);
        b.translate(-150, 0);
        drawBase(b);
        for (Zone z : ZONES) if (z.front == shownFront) drawZone(b, z);
        b.dispose();

        // tooltip
        if (tipA > 0.02 && hoverKey != null) {
            String label = MUSCLES.get(hoverKey).label;
            g.setFont(F_BOLD); int w = g.getFontMetrics().stringWidth(label) + 24;
            double tx = Math.min(mx + 16, 470 - w - 10), ty = my - 38 - (1 - tipA) * -8;
            Composite oc = g.getComposite();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) tipA));
            RoundRectangle2D tip = rr(tx, ty, w, 28, 14);
            shadow(g, tip, 4, 0.25);
            g.setColor(TEXT); g.fill(tip);
            ctext(g, label, new Rectangle2D.Double(tx, ty, w, 28), F_BOLD, DARK);
            g.setComposite(oc);
        }
    }

    void drawBase(Graphics2D g) {
        List<Shape> base = new ArrayList<>();
        base.add(new Ellipse2D.Double(122, 22, 56, 62));
        base.add(rr(138, 80, 24, 30, 8, 8));
        base.add(rr(100, 110, 100, 180, 44, 44));
        Shape arm = rr(58, 120, 34, 160, 24, 24); base.add(arm); base.add(mir(arm));
        Shape leg = rr(100, 284, 48, 236, 26, 26); base.add(leg); base.add(mir(leg));
        for (Shape s : base) {
            g.setColor(BASE); g.fill(s);
            g.setColor(al(LINE, 0.9)); g.setStroke(new BasicStroke(1.2f)); g.draw(s);
        }
    }

    void drawZone(Graphics2D g, Zone z) {
        double h = hov.getOrDefault(z.key, 0.0), s = sel.getOrDefault(z.key, 0.0);
        double pulse = 0.5 + 0.5 * Math.sin(time * 3.2);
        double hot = Math.max(0.55 * h, s);
        Color fill = lerp(BASE_HI, ACCENT, hot);
        for (Shape sh : z.shapes) {
            Rectangle2D bnd = sh.getBounds2D();
            double sc = 1 + 0.05 * h + 0.02 * s;
            AffineTransform at = new AffineTransform();
            at.translate(bnd.getCenterX(), bnd.getCenterY()); at.scale(sc, sc); at.translate(-bnd.getCenterX(), -bnd.getCenterY());
            Shape t = at.createTransformedShape(sh);
            if (s > 0.02) {                               // pulsing glow
                for (int w : new int[]{16, 10, 5}) {
                    g.setColor(al(ACCENT2, s * (0.05 + 0.07 * pulse) * (w == 5 ? 1.6 : 1)));
                    g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(t);
                }
            }
            g.setPaint(new GradientPaint((float) bnd.getX(), (float) bnd.getY(), lerp(fill, Color.WHITE, 0.12 * hot),
                    (float) bnd.getMaxX(), (float) bnd.getMaxY(), fill));
            g.fill(t);
            g.setColor(lerp(LINE, ACCENT2, hot)); g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(t);
        }
    }

    // ---- right column: title + food cards
    void drawRight(Graphics2D g) {
        MuscleInfo mi = info();
        double ta = ease(panelT / 0.35);
        Composite oc = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) ta));
        text(g, "TARGET MUSCLE", RX, 104 + (1 - ta) * 8, F_CAPS, ACCENT);
        text(g, mi.label, RX, 138 + (1 - ta) * 8, F_H1, TEXT);
        g.setFont(F_BODY);
        List<String> lines = wrap(mi.blurb, g.getFontMetrics(), RW);
        for (int i = 0; i < Math.min(2, lines.size()); i++) text(g, lines.get(i), RX, 162 + i * 18 + (1 - ta) * 8, F_BODY, MUTED);
        g.setComposite(oc);

        for (int i = 0; i < mi.foods.size(); i++) drawCard(g, mi.foods.get(i), i);
    }

    void drawCard(Graphics2D g, Food f, int i) {
        double p = ease((panelT - 0.08 * i - 0.05) / 0.45);
        if (p <= 0.001) return;
        double hv = cardHov[i];
        Rectangle2D.Double r = cardRect(i);
        Graphics2D c = (Graphics2D) g.create();
        c.translate(0, (1 - p) * 28 - hv * 3);
        c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) p));
        RoundRectangle2D card = rr(r.x, r.y, r.width, r.height, 22);
        shadow(c, card, 6, 0.10 + 0.14 * hv);
        c.setColor(lerp(CARD, CARD_HI, hv)); c.fill(card);
        c.setColor(lerp(LINE, ACCENT, hv * 0.75)); c.setStroke(new BasicStroke(1.2f)); c.draw(card);

        // coloured disc with initial
        Ellipse2D disc = new Ellipse2D.Double(r.x + 20, r.y + 16, 46, 46);
        c.setPaint(new GradientPaint((float) r.x + 20, (float) r.y + 16, lerp(f.tint, Color.WHITE, 0.25), (float) r.x + 66, (float) r.y + 62, f.tint));
        c.fill(disc);
        ctext(c, f.name.substring(0, 1), new Rectangle2D.Double(r.x + 20, r.y + 16, 46, 46), F_BIG, DARK);

        text(c, f.name, r.x + 80, r.y + 36, F_H2, TEXT);
        text(c, f.serving, r.x + 80, r.y + 55, F_SMALL, MUTED);
        rtext(c, String.valueOf(f.kcal), r.x + RW - 40, r.y + 40, F_BIG, TEXT);
        text(c, "kcal", r.x + RW - 34, r.y + 40, F_SMALL, MUTED);
        c.setFont(F_SMALL);
        text(c, fit(f.why, c.getFontMetrics(), RW - 40), r.x + 20, r.y + 78, F_SMALL, MUTED);

        // macro bars
        String[] names = {"Protein", "Carbs", "Fat"};
        int[] vals = {f.p, f.c, f.f}; int[] caps = {50, 60, 25};
        Color[] cols = {PCOL, CCOL, FCOL};
        for (int k = 0; k < 3; k++) {
            double bx = r.x + 20 + k * 144, by = r.y + 100;
            double fillP = ease((panelT - 0.2 - 0.08 * i - 0.06 * k) / 0.7);
            text(c, names[k] + "  " + vals[k] + " g", bx, by - 5, F_TINY, MUTED);
            c.setColor(new Color(0x2A3650)); c.fill(rr(bx, by, 130, 6, 6));
            double w = 130 * Math.min(1, vals[k] / (double) caps[k]) * fillP;
            if (w > 1) { c.setColor(cols[k]); c.fill(rr(bx, by, Math.max(6, w), 6, 6)); }
        }
        drawAddButton(c, f, i, r);
        c.dispose();
    }

    void drawAddButton(Graphics2D c, Food f, int i, Rectangle2D cardR) {
        double a = added.getOrDefault(f, 0.0);
        Rectangle2D.Double b = new Rectangle2D.Double(cardR.getX() + RW - 112, cardR.getY() + 78, 92, 28);
        boolean hot = ("add" + i).equals(hoverBtn);
        Color idle = hot ? new Color(0x3A4A6E) : new Color(0x2E3B5A);
        c.setColor(lerp(idle, GREEN, a)); c.fill(rr(b.x, b.y, b.width, b.height, 28));
        Color ink = lerp(TEXT, DARK, a);
        double cx = b.x + 22, cy = b.y + 14;
        c.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (a < 0.98) {                                      // plus that spins away
            Graphics2D pl = (Graphics2D) c.create();
            pl.translate(cx, cy); pl.rotate(a * Math.PI / 2);
            pl.setColor(al(ink, 1 - a));
            pl.draw(new Line2D.Double(-6, 0, 6, 0)); pl.draw(new Line2D.Double(0, -6, 0, 6));
            pl.dispose();
        }
        if (a > 0.3) {                                       // check that draws itself
            double q = clamp((a - 0.3) / 0.7);
            Path2D.Double chk = new Path2D.Double();
            Point2D.Double p1 = new Point2D.Double(cx - 6, cy), p2 = new Point2D.Double(cx - 2, cy + 5), p3 = new Point2D.Double(cx + 6, cy - 5);
            chk.moveTo(p1.x, p1.y);
            double s1 = Math.min(1, q * 2);
            chk.lineTo(p1.x + (p2.x - p1.x) * s1, p1.y + (p2.y - p1.y) * s1);
            if (q > 0.5) { double s2 = (q - 0.5) * 2; chk.lineTo(p2.x + (p3.x - p2.x) * s2, p2.y + (p3.y - p2.y) * s2); }
            c.setColor(ink); c.draw(chk);
        }
        Font bf = F_BOLD;
        text(c, "Add", b.x + 40, b.y + 19, bf, al(ink, 1 - a));
        text(c, "Added", b.x + 40, b.y + 19, bf, al(ink, a));
    }

    // ---- plate summary
    void drawPlate(Graphics2D g) {
        Rectangle2D.Double r = new Rectangle2D.Double(RX, 588, RW, 120);
        RoundRectangle2D box = rr(r.x, r.y, r.width, r.height, 22);
        g.setColor(PANEL); g.fill(box);
        g.setColor(LINE); g.setStroke(new BasicStroke(1.2f)); g.draw(box);
        text(g, "My plate", r.x + 20, r.y + 32, F_H2, TEXT);
        text(g, plate.isEmpty() ? "Tap Add on a food to build your plate" : plate.size() + (plate.size() == 1 ? " food added" : " foods added"),
                r.x + 110, r.y + 32, F_SMALL, MUTED);

        if (!plate.isEmpty()) {
            Rectangle2D.Double cr = clearRect();
            boolean hot = "clear".equals(hoverBtn);
            g.setColor(hot ? new Color(0x3A4A6E) : new Color(0x2E3B5A)); g.fill(rr(cr.x, cr.y, cr.width, cr.height, 26));
            ctext(g, "Clear", cr, F_SMALL, TEXT);
        }
        String[] lab = {"Calories", "Protein", "Carbs", "Fat"};
        double[] val = {dK, dP, dC, dF};
        String[] unit = {"", " g", " g", " g"};
        Color[] col = {TEXT, PCOL, CCOL, FCOL};
        for (int i = 0; i < 4; i++) {
            double x = r.x + 20 + i * 120;
            text(g, lab[i], x, r.y + 56, F_TINY, MUTED);
            text(g, Math.round(val[i]) + unit[i], x, r.y + 82, F_BIG, col[i]);
        }
        // protein target bar
        double bx = r.x + 20, by = r.y + 98, bw = RW - 40;
        g.setColor(new Color(0x2A3650)); g.fill(rr(bx, by, bw, 8, 8));
        double w = bw * dBar;
        if (w > 1) { g.setPaint(new GradientPaint((float) bx, 0, ACCENT2, (float) (bx + bw), 0, ACCENT)); g.fill(rr(bx, by, Math.max(8, w), 8, 8)); }
        rtext(g, goalName[goal] + " protein target: " + proteinTarget() + " g", r.x + RW - 20, r.y + 90, F_TINY, MUTED);
        text(g, "Based on " + gramsPerKg[goal] + " g of protein per kg x " + weightKg + " kg body weight", bx, r.y + 118, F_TINY, MUTED);
    }

    void drawRipples(Graphics2D g) {
        for (Ripple r : ripples) {
            double t = r.t / 0.6; if (t >= 1) continue;
            Graphics2D c = (Graphics2D) g.create();
            double rad = ease(t) * (r.clip == null ? 70 : 90);
            if (r.clip != null) {
                c.clip(r.clip);
                c.setColor(al(r.color, 0.35 * (1 - t)));
                c.fill(new Ellipse2D.Double(r.x - rad, r.y - rad, rad * 2, rad * 2));
            } else {
                c.setColor(al(r.color, 0.7 * (1 - t))); c.setStroke(new BasicStroke((float) (4 * (1 - t) + 1)));
                c.draw(new Ellipse2D.Double(r.x - rad, r.y - rad, rad * 2, rad * 2));
            }
            c.dispose();
        }
    }

    // ===================================================================== actions
    void setView(boolean front) { if (front != wantFront) { wantFront = front; flipP = 0; } }
    void selectMuscle(String key) { if (!key.equals(selKey)) { selKey = key; panelT = 0; } }
    void toggleFood(int i) {
        List<Food> fs = info().foods; if (i >= fs.size()) return;
        Food f = fs.get(i);
        if (!plate.remove(f)) plate.add(f);
        Rectangle2D.Double b = addRect(i);
        ripples.add(new Ripple(b.getCenterX(), b.getCenterY(), rr(b.x, b.y, b.width, b.height, 28), Color.WHITE));
    }

    String muscleAt(int x, int y) {
        if (flipP < 1 || x < 20 || x > 470) return null;
        double lx = x - BODY_X, ly = y - BODY_Y;
        List<Zone> list = new ArrayList<>(ZONES); Collections.reverse(list);
        for (Zone z : list) if (z.front == shownFront) for (Shape s : z.shapes) if (s.contains(lx, ly)) return z.key;
        return null;
    }

    void updateHover() {
        hoverBtn = null; hoverCard = -1; hoverKey = null;
        for (int i = 0; i < 3; i++) if (goalRect(i).contains(mx, my)) hoverBtn = "goal" + i;
        if (wMinusRect().contains(mx, my)) hoverBtn = "wminus";
        if (wPlusRect().contains(mx, my)) hoverBtn = "wplus";
        Rectangle2D.Double s = segRect();
        if (s.contains(mx, my)) hoverBtn = mx < s.x + 100 ? "seg0" : "seg1";
        if (!plate.isEmpty() && clearRect().contains(mx, my)) hoverBtn = "clear";
        int n = info().foods.size();
        for (int i = 0; i < n; i++) {
            if (panelT > 0.1 && addRect(i).contains(mx, my)) hoverBtn = "add" + i;
            if (panelT > 0.1 && cardRect(i).contains(mx, my)) hoverCard = i;
        }
        if (hoverBtn == null && hoverCard < 0) hoverKey = muscleAt(mx, my);
        setCursor(Cursor.getPredefinedCursor(hoverBtn != null || hoverCard >= 0 || hoverKey != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    void click() {
        updateHover();
        if (hoverBtn != null) {
            if (hoverBtn.equals("wminus") || hoverBtn.equals("wplus")) {
                changeWeight(hoverBtn.equals("wplus") ? 1 : -1);
                holdBtn = hoverBtn; holdT = 0; holdAcc = 0;
                Rectangle2D.Double br = hoverBtn.equals("wplus") ? wPlusRect() : wMinusRect();
                ripples.add(new Ripple(mx, my, new Ellipse2D.Double(br.x, br.y, br.width, br.height), Color.WHITE));
            } else if (hoverBtn.startsWith("goal")) {
                int i = hoverBtn.charAt(4) - '0'; goal = i;
                ripples.add(new Ripple(mx, my, rr(goalBox().x, goalBox().y, goalBox().width, goalBox().height, 42), Color.WHITE));
            } else if (hoverBtn.startsWith("seg")) {
                setView(hoverBtn.equals("seg0"));
                Rectangle2D.Double s = segRect();
                ripples.add(new Ripple(mx, my, rr(s.x, s.y, s.width, s.height, 38), Color.WHITE));
            } else if (hoverBtn.startsWith("add")) {
                toggleFood(hoverBtn.charAt(3) - '0');
            } else if (hoverBtn.equals("clear")) {
                plate.clear();
                Rectangle2D.Double c = clearRect();
                ripples.add(new Ripple(mx, my, rr(c.x, c.y, c.width, c.height, 26), Color.WHITE));
            }
        } else if (hoverCard >= 0) {
            toggleFood(hoverCard);
        } else if (hoverKey != null) {
            selectMuscle(hoverKey);
            ripples.add(new Ripple(mx, my, null, ACCENT2));
        }
    }

    @Override public void mouseMoved(MouseEvent e) { mx = e.getX(); my = e.getY(); updateHover(); }
    @Override public void mouseDragged(MouseEvent e) { mouseMoved(e); }
    @Override public void mousePressed(MouseEvent e) { requestFocusInWindow(); mx = e.getX(); my = e.getY(); click(); }
    @Override public void mouseExited(MouseEvent e) { mx = my = -1; updateHover(); }
    @Override public void mouseClicked(MouseEvent e) { }
    @Override public void mouseReleased(MouseEvent e) { holdBtn = null; }
    @Override public void mouseEntered(MouseEvent e) { }
    @Override public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_F) setView(!wantFront);
        if (e.getKeyCode() == KeyEvent.VK_UP) changeWeight(1);
        if (e.getKeyCode() == KeyEvent.VK_DOWN) changeWeight(-1);
        if (e.getKeyCode() >= KeyEvent.VK_1 && e.getKeyCode() <= KeyEvent.VK_3) goal = e.getKeyCode() - KeyEvent.VK_1;
    }
    @Override public void keyReleased(KeyEvent e) { }
    @Override public void keyTyped(KeyEvent e) { }

    // ===================================================================== main
    public static void main(String[] args) throws Exception {
        boolean shot = args.length >= 2 && args[0].equals("--shot");
        SwingUtilities.invokeAndWait(() -> {
            JFrame fr = new JFrame("GymFuel");
            GymFuel app = new GymFuel();
            fr.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            fr.setContentPane(app);
            fr.setResizable(false);
            fr.pack();
            fr.setLocationRelativeTo(null);
            fr.setVisible(true);
            if (shot) runShots(app, args[1]);
        });
    }

    /** Scripted run used to render preview images without a human: java GymFuel --shot <folder> */
    static void runShots(GymFuel app, String dir) {
        new File(dir).mkdirs();
        long t0 = System.currentTimeMillis();
        boolean[] done = new boolean[12];
        new javax.swing.Timer(50, e -> {
            long t = System.currentTimeMillis() - t0;
            if (t > 600 && !done[0]) { done[0] = true; app.selectMuscle("chest"); app.goal = 0; app.weightKg = 80; app.toggleFood(0); app.toggleFood(2); app.mx = 232; app.my = 290; app.updateHover(); }
            if (t > 2400 && !done[1]) { done[1] = true; save(app, dir + "/1_chest_bulk.png"); app.selectMuscle("quads"); app.goal = 1; app.mx = 215; app.my = 500; app.updateHover(); }
            if (t > 2500 && !done[2]) { done[2] = true; app.toggleFood(1); }
            if (t > 4300 && !done[3]) { done[3] = true; save(app, dir + "/2_quads_maintain.png"); app.setView(false); }
            if (t > 4900 && !done[4]) { done[4] = true; app.selectMuscle("lats"); app.goal = 2; app.toggleFood(1); app.mx = 232; app.my = 330; app.updateHover(); }
            if (t > 6700 && !done[5]) { done[5] = true; save(app, dir + "/3_back_lats_cut.png"); app.selectMuscle("glutes"); app.toggleFood(2); app.mx = -50; app.my = -50; app.updateHover(); }
            if (t > 8500 && !done[6]) { done[6] = true; save(app, dir + "/4_back_glutes.png"); app.setView(true); }
            if (t > 9100 && !done[7]) { done[7] = true; app.selectMuscle("biceps"); app.goal = 0; app.mx = -50; app.my = -50; app.updateHover(); }
            if (t > 10900) { save(app, dir + "/5_biceps.png"); System.exit(0); }
        }).start();
    }

    static void save(GymFuel app, String path) {
        try {
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics(); app.paint(g); g.dispose();
            ImageIO.write(img, "png", new File(path));
        } catch (Exception ex) { ex.printStackTrace(); }
    }
}
