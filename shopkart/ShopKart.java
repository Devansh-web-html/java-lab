// Shop Kart - a complete e-commerce site in one Java file. No dependencies.
// Run (JDK 17+):  java ShopKart.java        then open http://localhost:8080
// Optional port:  java ShopKart.java 9000
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ShopKart {
    static final String CUR = "&#8377;";
    record Product(int id, String name, String cat, int price, String color, String blurb) {}
    record Order(int id, String sid, String name, String addr, List<String> lines, int total) {}

    static final List<Product> PRODUCTS = List.of(
        new Product(1, "Basmati Rice, 5 kg", "Groceries", 599, "#D99A1E", "Long-grain, aged for fluffy biryani and pulao."),
        new Product(2, "Whole Wheat Atta, 5 kg", "Groceries", 289, "#B7791F", "Stone-ground flour for soft rotis."),
        new Product(3, "Toor Dal, 1 kg", "Groceries", 169, "#C2556B", "Clean, polished dal that cooks evenly."),
        new Product(4, "Mustard Oil, 1 L", "Groceries", 189, "#6B7F3A", "Cold-pressed with a strong, pungent flavour."),
        new Product(5, "Bananas, 1 dozen", "Fresh Produce", 69, "#E0B400", "Ripe and sweet. Picked this week."),
        new Product(6, "Tomatoes, 1 kg", "Fresh Produce", 45, "#C0392B", "Firm, red and good for curries and salads."),
        new Product(7, "Onions, 2 kg", "Fresh Produce", 70, "#7A3E65", "Dry, well-cured onions that store for weeks."),
        new Product(8, "Apples, 1 kg", "Fresh Produce", 180, "#2E7D77", "Crisp and juicy, 4 to 5 apples."),
        new Product(9, "Wireless Earbuds", "Electronics", 1499, "#2B50AA", "Bluetooth 5.3, 24 hours of battery with the case."),
        new Product(10, "Power Bank, 20000 mAh", "Electronics", 1799, "#3F4E63", "Charges a phone about four times."),
        new Product(11, "Smart LED Bulb, 9 W", "Electronics", 399, "#D99A1E", "Wi-Fi bulb with 16 million colours."),
        new Product(12, "USB-C Charger, 30 W", "Electronics", 899, "#4A90C2", "Fast charging for phones and tablets."),
        new Product(13, "Cotton T-Shirt", "Fashion", 499, "#2E7D77", "Soft 180 GSM cotton in a regular fit."),
        new Product(14, "Slim-Fit Jeans", "Fashion", 1299, "#2B50AA", "Stretch denim, mid rise."),
        new Product(15, "Canvas Sneakers", "Fashion", 1599, "#C2556B", "Light, flexible and easy to wash."),
        new Product(16, "Everyday Backpack", "Fashion", 999, "#3F4E63", "25 L with a padded laptop sleeve."),
        new Product(17, "Pressure Cooker, 5 L", "Home and Kitchen", 1899, "#7A3E65", "Stainless steel, induction friendly."),
        new Product(18, "Non-Stick Tawa", "Home and Kitchen", 649, "#3F4E63", "28 cm with a cool-touch handle."),
        new Product(19, "Glass Jars, set of 3", "Home and Kitchen", 549, "#4A90C2", "Airtight lids for spices and snacks."),
        new Product(20, "Cotton Bedsheet, double", "Home and Kitchen", 899, "#6B7F3A", "Two pillow covers included."),
        new Product(21, "Herbal Shampoo, 340 ml", "Personal Care", 299, "#6B7F3A", "Gentle daily wash with amla and bhringraj."),
        new Product(22, "Sunscreen SPF 50, 100 ml", "Personal Care", 399, "#D99A1E", "Lightweight and non-greasy."),
        new Product(23, "Aloe Face Wash, 150 ml", "Personal Care", 199, "#2E7D77", "Cleans without drying your skin."),
        new Product(24, "Beard Trimmer", "Personal Care", 1299, "#2B50AA", "Cordless, with 20 length settings.")
    );
    static final List<String> CATS = List.of("Groceries", "Fresh Produce", "Electronics", "Fashion", "Home and Kitchen", "Personal Care");
    static final Map<String, Map<Integer, Integer>> CARTS = new ConcurrentHashMap<>();
    static final Map<Integer, Order> ORDERS = new ConcurrentHashMap<>();
    static final AtomicInteger NEXT_ORDER = new AtomicInteger(1000);
    static final Path ASSET_DIR = Path.of("assets");   // run ShopKart from the project folder
    record Photo(String path, long checked) {}
    static final Map<Integer, Photo> PHOTOS = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        s.createContext("/", ShopKart::handle);
        s.setExecutor(Executors.newFixedThreadPool(8));
        s.start();
        System.out.println("Shop Kart is running at http://localhost:" + port);
    }

    // ---------- routing ----------
    static void handle(HttpExchange x) throws IOException {
        try {
            String path = x.getRequestURI().getPath(), m = x.getRequestMethod();
            if (m.equals("GET") && path.startsWith("/assets/")) { serveAsset(x, path); return; }
            Map<String, String> q = parse(x.getRequestURI().getRawQuery());
            Map<String, String> f = m.equals("POST") ? parse(new String(x.getRequestBody().readNBytes(20000), UTF_8)) : Map.of();
            String sid = sid(x);
            Map<Integer, Integer> cart = CARTS.computeIfAbsent(sid, k -> new ConcurrentHashMap<>());
            switch (m + " " + path) {
                case "GET /" -> send(x, 200, shop(q, cart, x.getRequestURI().toString()));
                case "GET /product" -> {
                    Product p = byId(num(q.get("id")));
                    if (p == null) send(x, 404, notFound(cart));
                    else send(x, 200, productPage(p, cart, x.getRequestURI().toString()));
                }
                case "GET /cart" -> send(x, 200, cartPage(cart, "", Map.of()));
                case "POST /add" -> {
                    Product p = byId(num(f.get("id")));
                    if (p != null) cart.merge(p.id(), 1, (a, b) -> Math.min(20, a + b));
                    redirect(x, safe(f.get("next")));
                }
                case "POST /update" -> {
                    Product p = byId(num(f.get("id")));
                    int qty = Math.min(20, num(f.get("qty")));
                    if (p != null) { if (qty <= 0) cart.remove(p.id()); else cart.put(p.id(), qty); }
                    redirect(x, "/cart");
                }
                case "POST /checkout" -> checkout(x, f, cart, sid);
                case "GET /order" -> {
                    Order o = ORDERS.get(num(q.get("id")));
                    if (o == null || !o.sid().equals(sid)) send(x, 404, notFound(cart));
                    else send(x, 200, orderPage(o, cart));
                }
                default -> send(x, 404, notFound(cart));
            }
        } catch (Exception e) {
            e.printStackTrace();
            x.sendResponseHeaders(500, -1);
            x.close();
        }
    }

    static void checkout(HttpExchange x, Map<String, String> f, Map<Integer, Integer> cart, String sid) throws IOException {
        var ls = lines(cart);
        if (ls.isEmpty()) { redirect(x, "/cart"); return; }
        String name = f.getOrDefault("name", "").trim(), email = f.getOrDefault("email", "").trim(), addr = f.getOrDefault("addr", "").trim();
        if (name.isEmpty() || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") || addr.length() < 8) {
            send(x, 400, cartPage(cart, "Enter your name, a valid email and a full delivery address.", f));
            return;
        }
        int sub = 0;
        List<String> out = new ArrayList<>();
        for (var e : ls) { sub += e.getKey().price() * e.getValue(); out.add(e.getValue() + " x " + e.getKey().name()); }
        Order o = new Order(NEXT_ORDER.incrementAndGet(), sid, name, addr, out, sub + ship(sub));
        ORDERS.put(o.id(), o);
        cart.clear();
        redirect(x, "/order?id=" + o.id());
    }

    // ---------- pages ----------
    static String shop(Map<String, String> q, Map<Integer, Integer> cart, String uri) {
        String cat = q.getOrDefault("cat", ""), term = q.getOrDefault("q", "").trim().toLowerCase();
        StringBuilder b = new StringBuilder("<h1>Everything you need, in one cart.</h1><p class=mut>Shop by category. Free delivery over ")
            .append(money(499)).append(".</p><nav class=chips aria-label=Categories><a href=/ class='").append(cat.isEmpty() ? "on" : "").append("'>All</a>");
        for (String c : CATS)
            b.append("<a href='/?cat=").append(URLEncoder.encode(c, UTF_8)).append("' class='").append(c.equals(cat) ? "on" : "").append("'>").append(esc(c)).append("</a>");
        b.append("</nav>");
        int shown = 0;
        for (String c : CATS) {
            List<Product> list = PRODUCTS.stream().filter(p -> p.cat().equals(c) && (cat.isEmpty() || cat.equals(c))
                && (term.isEmpty() || (p.name() + " " + p.cat() + " " + p.blurb()).toLowerCase().contains(term))).toList();
            if (list.isEmpty()) continue;
            shown += list.size();
            b.append("<h2 class=sec>").append(esc(c)).append("<small>").append(list.size()).append(" items</small></h2><div class=grid>");
            for (Product p : list)
                b.append("<article class=card>").append(artFor(p)).append("<div class=info><h3><a href='/product?id=").append(p.id()).append("'>")
                 .append(esc(p.name())).append("</a></h3><p class=mut>").append(esc(p.blurb())).append("</p><div class=row><b>").append(money(p.price()))
                 .append("</b><form method=post action=/add><input type=hidden name=id value=").append(p.id()).append("><input type=hidden name=next value=\"").append(esc(uri))
                 .append("\"><button>Add to cart</button></form></div></div></article>");
            b.append("</div>");
        }
        if (shown == 0) b.append("<p>No products match that search. <a href=/>See all categories</a>.</p>");
        return page("Shop", b.toString(), cart, term);
    }

    static String cartPage(Map<Integer, Integer> cart, String err, Map<String, String> f) {
        var ls = lines(cart);
        if (ls.isEmpty())
            return page("Cart", "<h1>Your cart is empty</h1><p class=mut>Pick something for the table.</p><p><a class=btn href=/>Browse the shop</a></p>", cart, "");
        StringBuilder b = new StringBuilder("<h1>Your cart</h1><div class=cols><section>");
        int sub = 0;
        for (var e : ls) {
            Product p = e.getKey(); int qty = e.getValue();
            sub += p.price() * qty;
            b.append("<div class=item>").append(artFor(p)).append("<div><b>").append(esc(p.name()))
             .append("</b><div class=mut>").append(money(p.price())).append(" each</div></div><div class=qty>")
             .append(qtyForm(p, qty - 1, "&#8722;", "Decrease quantity")).append("<span>").append(qty).append("</span>")
             .append(qtyForm(p, qty + 1, "+", "Increase quantity")).append(qtyForm(p, 0, "Remove", "Remove from cart")).append("</div></div>");
        }
        int sh = ship(sub);
        b.append("</section><aside class=panel><h2>Checkout</h2><dl><dt>Subtotal</dt><dd>").append(money(sub)).append("</dd><dt>Delivery</dt><dd>")
         .append(sh == 0 ? "Free" : money(sh)).append("</dd><dt>Total</dt><dd><b>").append(money(sub + sh)).append("</b></dd></dl>")
         .append("<form method=post action=/checkout>");
        if (!err.isEmpty()) b.append("<p class=err role=alert>").append(esc(err)).append("</p>");
        b.append(field("Full name", "name", "text", f)).append(field("Email", "email", "email", f))
         .append("<label>Delivery address<textarea name=addr rows=3 required>").append(esc(f.getOrDefault("addr", ""))).append("</textarea></label>")
         .append("<button class=wide>Place order</button></form><p class=mut><small>This is a demo store. No payment is taken.</small></p></aside></div>");
        return page("Cart", b.toString(), cart, "");
    }

    static String orderPage(Order o, Map<Integer, Integer> cart) {
        StringBuilder b = new StringBuilder("<div class=panel style='max-width:560px'><h1>Thanks, ").append(esc(o.name())).append(".</h1><p>Order #")
            .append(o.id()).append(" is confirmed. We will pack it within two working days.</p><ul>");
        for (String l : o.lines()) b.append("<li>").append(esc(l)).append("</li>");
        b.append("</ul><p><b>Order total: ").append(money(o.total())).append("</b></p><p class=mut>Delivering to:<br>")
         .append(esc(o.addr()).replace("\n", "<br>")).append("</p><a class=btn href=/>Keep shopping</a></div>");
        return page("Order confirmed", b.toString(), cart, "");
    }

    static String notFound(Map<Integer, Integer> cart) {
        return page("Not found", "<h1>Page not found</h1><p><a class=btn href=/>Back to the shop</a></p>", cart, "");
    }

    static String page(String title, String body, Map<Integer, Integer> cart, String term) {
        int n = cart.values().stream().mapToInt(i -> i).sum();
        return "<!doctype html><html lang=en><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>"
            + esc(title) + " &#8211; ShopKart</title><link rel=preconnect href=https://fonts.googleapis.com><link rel=stylesheet href='https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:wght@400;600;800&display=swap'><style>"
            + CSS + "</style></head><body><header><div class=bar><a class=logo href=/>ShopKart</a><form class=search action=/ role=search><input type=search name=q placeholder='Search groceries, phones, shoes' aria-label='Search products' value=\""
            + esc(term) + "\"></form><a class=cartlink href=/cart>Cart <span class=count>" + n + "</span></a></div></header><main>" + body
            + "</main><footer>&#169; ShopKart Everyday essentials at fair prices.</footer></body></html>";
    }

    // ---------- helpers ----------
    static String qtyForm(Product p, int qty, String label, String aria) {
        return "<form method=post action=/update><input type=hidden name=id value=" + p.id() + "><input type=hidden name=qty value=" + qty
            + "><button class=ghost aria-label='" + aria + ": " + esc(p.name()) + "'>" + label + "</button></form>";
    }

    static String field(String label, String name, String type, Map<String, String> f) {
        return "<label>" + label + "<input name=" + name + " type=" + type + " required value=\"" + esc(f.getOrDefault(name, "")) + "\"></label>";
    }

    // ---------- product photos ----------
    // Path of /assets/products/<id>.<ext> if a photo exists, otherwise null. Cached for 60 seconds.
    static String photoPath(Product p) {
        long now = System.currentTimeMillis();
        Photo cached = PHOTOS.get(p.id());
        if (cached != null && now - cached.checked() < 60_000) return cached.path();
        String found = null;
        for (String ext : List.of("jpg", "jpeg", "png", "webp")) {
            if (Files.isRegularFile(ASSET_DIR.resolve("products").resolve(p.id() + "." + ext))) {
                found = "/assets/products/" + p.id() + "." + ext;
                break;
            }
        }
        PHOTOS.put(p.id(), new Photo(found, now));
        return found;
    }

    // Image block for a product. The illustration stays underneath as the fallback (no photo,
    // still loading, or broken link). The photo fades in once it has loaded.
    static String artFor(Product p) {
        String photo = photoPath(p);
        String tint = "style='--c:" + p.color() + "'";
        String illustration = "<div class=ph>" + vessel(p) + "</div>";
        if (photo == null) return "<div class=art " + tint + ">" + illustration + "</div>";
        return "<div class='art has-photo' " + tint + ">" + illustration
            + "<img src='" + photo + "' alt='" + esc(p.name()) + "' width=400 height=400 loading=lazy decoding=async"
            + " onload=\"this.parentNode.classList.add('loaded');this.classList.add('ok')\""
            + " onerror=\"this.parentNode.classList.add('loaded');this.remove()\"></div>";
    }

    // Photo credit written by tools/fetch_product_images.py, or null if there is none.
    static String creditText(Product p) {
        Path f = ASSET_DIR.resolve("products").resolve(p.id() + ".credit.txt");
        try {
            return Files.isRegularFile(f) ? Files.readString(f, UTF_8).trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

    static String productPage(Product p, Map<Integer, Integer> cart, String uri) {
        StringBuilder b = new StringBuilder("<nav class=crumb><a href=/>Shop</a> / <a href='/?cat=")
            .append(URLEncoder.encode(p.cat(), UTF_8)).append("'>").append(esc(p.cat())).append("</a></nav>")
            .append("<div class=detail><div class=detailart style='--c:").append(p.color()).append("'>")
            .append(artFor(p)).append("</div><div>")
            .append("<h1 class=dname>").append(esc(p.name())).append("</h1>")
            .append("<p class=mut>").append(esc(p.blurb())).append("</p>")
            .append("<p class=row><b>").append(money(p.price())).append("</b></p>")
            .append("<form method=post action=/add><input type=hidden name=id value=").append(p.id())
            .append("><input type=hidden name=next value=\"").append(esc("/product?id=" + p.id()))
            .append("\"><button>Add to cart</button></form>");
        String credit = creditText(p);
        if (credit != null) b.append("<p class=credit>").append(esc(credit)).append("</p>");
        b.append("</div></div>");
        return page(p.name(), b.toString(), cart, "");
    }

    // ---------- static assets ----------
    static void serveAsset(HttpExchange x, String path) throws IOException {
        Path root = ASSET_DIR.toAbsolutePath().normalize();
        Path file = root.resolve(path.substring("/assets/".length())).normalize();
        String type = file.getFileName() == null ? null : mime(file.getFileName().toString());
        if (!file.startsWith(root) || type == null || !Files.isRegularFile(file)) {
            x.sendResponseHeaders(404, -1);
            x.close();
            return;
        }
        byte[] body = Files.readAllBytes(file);
        x.getResponseHeaders().set("Content-Type", type);
        x.getResponseHeaders().set("Cache-Control", "public, max-age=604800");
        x.sendResponseHeaders(200, body.length);
        try (OutputStream o = x.getResponseBody()) { o.write(body); }
    }

    static String mime(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        return null;   // images only, so nothing else in assets/ can be served
    }

    static String vessel(Product p) {
        String shape = switch (p.cat()) {
            case "Groceries" -> "<path d='M22 34h56l-4 52H26z'/><path d='M36 34v-6q0-14 14-14t14 14v6' fill='none' stroke='currentColor' stroke-width='6'/>";
            case "Fresh Produce" -> "<circle cx='50' cy='60' r='30'/><path d='M50 32q0-16 16-20q2 16-16 20z'/>";
            case "Electronics" -> "<rect x='16' y='24' width='68' height='46' rx='5'/><path d='M6 78h88l-6 10H12z'/>";
            case "Fashion" -> "<path d='M34 14L8 30l9 17 13-7v46h40V40l13 7 9-17-26-16q-4 11-16 11t-16-11z'/>";
            case "Home and Kitchen" -> "<path d='M20 40h60v36q0 10-10 10H30q-10 0-10-10z'/><path d='M8 50h12M80 50h12M34 30h32' stroke='currentColor' stroke-width='6' stroke-linecap='round'/>";
            default -> "<rect x='38' y='8' width='24' height='16' rx='3'/><path d='M30 30h40v54q0 8-8 8H38q-8 0-8-8z'/><rect x='38' y='46' width='24' height='20' rx='3' fill='#fff' fill-opacity='.4'/>";
        };
        return "<svg viewBox='0 0 100 100' fill='currentColor' aria-hidden=true>" + shape + "</svg>";
    }

    static List<Map.Entry<Product, Integer>> lines(Map<Integer, Integer> cart) {
        List<Map.Entry<Product, Integer>> out = new ArrayList<>();
        for (int id : new TreeSet<>(cart.keySet())) {
            Product p = byId(id);
            int qty = cart.getOrDefault(id, 0);
            if (p != null && qty > 0) out.add(Map.entry(p, qty));
        }
        return out;
    }

    static Product byId(int id) { return PRODUCTS.stream().filter(p -> p.id() == id).findFirst().orElse(null); }
    static int ship(int sub) { return sub >= 499 ? 0 : 40; }
    static String money(int v) { return CUR + v; }
    static int num(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; } }
    static String safe(String next) { return next != null && next.startsWith("/") && !next.startsWith("//") ? next : "/"; }
    static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }

    static Map<String, String> parse(String s) {
        Map<String, String> m = new HashMap<>();
        if (s == null || s.isBlank()) return m;
        for (String kv : s.split("&")) {
            int i = kv.indexOf('=');
            m.put(URLDecoder.decode(i < 0 ? kv : kv.substring(0, i), UTF_8), i < 0 ? "" : URLDecoder.decode(kv.substring(i + 1), UTF_8));
        }
        return m;
    }

    static String sid(HttpExchange x) {
        String c = x.getRequestHeaders().getFirst("Cookie");
        if (c != null) for (String p : c.split(";\\s*")) if (p.startsWith("sid=")) return p.substring(4);
        String id = UUID.randomUUID().toString();
        x.getResponseHeaders().add("Set-Cookie", "sid=" + id + "; Path=/; HttpOnly; SameSite=Lax");
        return id;
    }

    static void send(HttpExchange x, int code, String html) throws IOException {
        byte[] b = html.getBytes(UTF_8);
        x.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        x.sendResponseHeaders(code, b.length);
        try (OutputStream o = x.getResponseBody()) { o.write(b); }
    }

    static void redirect(HttpExchange x, String to) throws IOException {
        x.getResponseHeaders().set("Location", to);
        x.sendResponseHeaders(303, -1);
        x.close();
    }

    // ---------- styles ----------
    static final String CSS = """
        :root{--bg:#F4F6F6;--ink:#16213E;--mut:#5B667A;--line:#DCE2E4;--acc:#2B50AA;--acc2:#1E3A82}
        *{box-sizing:border-box}
        body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.55 "Bricolage Grotesque",system-ui,-apple-system,"Segoe UI",sans-serif}
        a{color:inherit}
        header{position:sticky;top:0;z-index:5;background:rgba(244,246,246,.92);backdrop-filter:blur(8px);border-bottom:1px solid var(--line)}
        .bar{max-width:1120px;margin:auto;padding:12px 20px;display:flex;gap:20px;align-items:center}
        .logo{font-weight:800;font-size:1.4rem;letter-spacing:-.03em;text-decoration:none}
        .search{flex:1;max-width:380px;margin-left:auto}
        input,textarea{font:inherit;width:100%;padding:10px 12px;border:1px solid var(--line);border-radius:10px;background:#fff;color:inherit}
        :focus-visible{outline:3px solid var(--acc);outline-offset:2px}
        .cartlink{text-decoration:none;font-weight:600;padding:8px 14px;border:1px solid var(--line);border-radius:99px;background:#fff;white-space:nowrap}
        .count{display:inline-block;min-width:1.5em;text-align:center;background:var(--ink);color:#fff;border-radius:99px;font-size:.85rem;margin-left:4px}
        main{max-width:1120px;margin:auto;padding:36px 20px 72px}
        h1{font-size:clamp(2rem,5.5vw,3.6rem);line-height:1.03;letter-spacing:-.04em;font-weight:800;margin:0 0 14px;max-width:18ch}
        h2{margin:0 0 12px;letter-spacing:-.02em}h3{margin:0 0 4px;font-size:1.1rem}
        .mut{color:var(--mut);margin:0 0 12px}
        .chips{display:flex;flex-wrap:wrap;gap:8px;margin:20px 0 28px}
        .chips a{padding:6px 16px;border:1px solid var(--line);border-radius:99px;text-decoration:none;background:#fff}
        .chips a.on{background:var(--ink);color:#fff;border-color:var(--ink)}
        .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:22px}
        .card{background:#fff;border:1px solid var(--line);border-radius:20px;overflow:hidden;display:flex;flex-direction:column}
        .art{color:var(--c);background:color-mix(in srgb,var(--c) 14%,#fff);display:grid;place-items:center}
        .card .art{aspect-ratio:1;padding:14%}
        .art svg{width:100%;height:100%}
        .info{padding:16px 18px 18px;display:flex;flex-direction:column;flex:1}
        .info .mut{flex:1;font-size:.95rem}
        .row{display:flex;justify-content:space-between;align-items:center}
        .row b{font-size:1.2rem}
        button,.btn{font:inherit;font-weight:600;border:0;border-radius:10px;padding:9px 16px;background:var(--acc);color:#fff;cursor:pointer;text-decoration:none;display:inline-block}
        button:hover,.btn:hover{background:var(--acc2)}
        button.ghost{background:transparent;color:var(--ink);border:1px solid var(--line);padding:5px 11px}
        button.ghost:hover{background:#fff}
        .wide{width:100%;margin-top:14px;padding:12px}
        .cols{display:grid;grid-template-columns:1fr 360px;gap:36px;align-items:start}
        .item{display:grid;grid-template-columns:72px 1fr auto;gap:16px;align-items:center;padding:14px 0;border-bottom:1px solid var(--line)}
        .item .art{width:72px;height:72px;border-radius:14px;padding:10px}
        .qty{display:flex;gap:6px;align-items:center}.qty span{min-width:1.6em;text-align:center;font-weight:600}
        .panel{background:#fff;border:1px solid var(--line);border-radius:20px;padding:24px}
        dl{display:grid;grid-template-columns:1fr auto;gap:6px 12px;margin:0 0 18px}dt{color:var(--mut)}dd{margin:0;text-align:right}
        label{display:block;margin-top:12px;font-weight:600;font-size:.95rem}label input,label textarea{margin-top:4px;font-weight:400}
        .err{background:#FDECEE;color:#9B1C31;padding:10px 12px;border-radius:10px;margin:0}
        .sec{margin:40px 0 16px;font-size:1.5rem}.sec small{color:var(--mut);font-weight:400;font-size:1rem;margin-left:8px}
        .art{position:relative;overflow:hidden}
        .ph{position:absolute;inset:0;display:grid;place-items:center;padding:14%}
        .art img{position:absolute;inset:0;z-index:2;width:100%;height:100%;object-fit:contain;padding:8%;background:#fff;opacity:0;transition:opacity .35s ease}
        .art img.ok{opacity:1}
        .art.has-photo:not(.loaded)::before{content:"";position:absolute;inset:0;z-index:1;background:linear-gradient(90deg,transparent,rgba(255,255,255,.6),transparent);background-size:200% 100%;animation:shimmer 1.2s linear infinite}
        @keyframes shimmer{from{background-position:200% 0}to{background-position:-200% 0}}
        .info h3 a{color:inherit;text-decoration:none}.info h3 a:hover{text-decoration:underline}
        .crumb{font-size:.9rem;color:var(--mut);margin:0 0 18px}.crumb a{color:var(--mut)}
        .detail{display:grid;grid-template-columns:minmax(0,1fr) 380px;gap:36px;align-items:start}
        .detailart{border-radius:20px;overflow:hidden;border:1px solid var(--line)}
        .detailart .art{aspect-ratio:1;padding:14%}
        .dname{font-size:clamp(1.6rem,4vw,2.4rem);max-width:none}
        .credit{font-size:.85rem;color:var(--mut);margin-top:16px}
        @media(prefers-reduced-motion:reduce){.art.has-photo:not(.loaded)::before{animation:none}.art img{transition:none}}
        footer{text-align:center;color:var(--mut);padding:28px;border-top:1px solid var(--line)}
        @media(max-width:820px){.cols{grid-template-columns:1fr}.bar{flex-wrap:wrap}.search{order:3;max-width:none;flex-basis:100%;margin:0}.cartlink{margin-left:auto}.item{grid-template-columns:56px 1fr}.item .art{width:56px;height:56px}.qty{grid-column:1/-1}}
        """;
}
