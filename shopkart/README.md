# ShopKart — RTU Advanced Java Mini Project

A runnable Java e-commerce demo based on the supplied ShopSphere guide. This starter version uses Java's built-in `HttpServer`, so it can run immediately without installing Tomcat, Maven, or MySQL. It demonstrates product browsing, category filtering, search, session-like cart state using a cookie, quantity updates, checkout validation, and order confirmation.

> Important: This is the runnable starter/demo version, not yet the full Jakarta Servlet + JSP + MySQL enterprise implementation described in the guide. The syllabus mapping below identifies what is already demonstrated and what should be added for the full submission.

## Requirements
- JDK 17 or newer
- A modern browser

## Run on Windows
1. Extract this ZIP.
2. Open Command Prompt or PowerShell in this folder.
3. Check Java: `java -version`
4. Start the app:
   ```bash
   java ShopKart.java
   ```
5. Open `http://localhost:8080`

To use another port:
```bash
java ShopKart.java 9000
```
Then open `http://localhost:9000`.

If source-file launch is unsupported by your JDK, compile and run:
```bash
javac ShopKart.java
java ShopKart
```

## Features
- Responsive shopping catalogue with sample products
- Product category filters and keyword search
- Add to cart
- Increase/decrease quantities and remove items
- Delivery charge calculation (free over ₹499; otherwise ₹40)
- Checkout form with basic validation
- Order confirmation page
- HTML escaping for user-entered text
- Cookie-based visitor/cart identification
- Java HTTP server and request routing

## Main routes
- `GET /` — product catalogue and search
- `POST /add` — add product
- `GET /cart` — view cart and checkout form
- `POST /update` — update/remove quantity
- `POST /checkout` — validate and place demo order
- `GET /order?id=...` — order confirmation

## RTU syllabus mapping

| RTU topic | Demonstration in this starter |
|---|---|
| Java / OOP | `Product`, `Order` records, methods, collections |
| `java.net` | `HttpServer`, `InetSocketAddress`, URL encoding/decoding |
| HTTP | GET/POST routing, request parameters, redirects, response headers |
| Client-server | Browser acts as client; Java process serves requests |
| Session/cookies | `sid` cookie associates a cart with a browser |
| MVC / layered architecture | Basic route handling and page-generation methods are in one class here; refactor into Controller/Service/DAO for the full project |
| JDBC / MySQL | Not included in this starter; add a JDBC DAO and MySQL schema for persistent data |
| Servlet/JSP/Tomcat | Not included in this starter; migrate routes to Jakarta Servlets and views to JSP for syllabus-complete submission |
| Swing admin | Planned extension |
| Socket/ServerSocket | Planned inventory-server extension |
| RMI / Registry | Planned remote-inventory extension |
| Serialization | Planned product/order transfer extension |
| Internationalization | Planned `ResourceBundle` and locale-formatting extension |
| Filters/listeners/JSTL | Planned Servlet/JSP extension |
| Applets | Historical concept only; Java applets are obsolete and should not be used as a dependency |

## Suggested full-project structure for the next version

```text
shopsphere/
├── pom.xml
├── src/main/java/com/shopsphere/
│   ├── model/       Product.java, User.java, CartItem.java, Order.java
│   ├── dao/         ProductDAO.java, UserDAO.java, OrderDAO.java
│   ├── service/     ProductService.java, CartService.java, OrderService.java
│   ├── controller/  ProductServlet.java, CartServlet.java, CheckoutServlet.java
│   ├── filter/      AuthenticationFilter.java, AdminFilter.java
│   ├── listener/    AppContextListener.java, SessionListener.java
│   ├── network/     InventoryServer.java, InventoryClient.java
│   ├── rmi/         InventoryService.java, InventoryServiceImpl.java
│   └── desktop/     AdminDashboard.java
├── src/main/webapp/
│   ├── WEB-INF/web.xml
│   ├── WEB-INF/views/
│   │   ├── products.jsp
│   │   ├── cart.jsp
│   │   └── checkout.jsp
│   └── fragments/   header.jspf, footer.jspf
└── database/schema.sql
```

## Before a real deployment
This is a college demo, not a production payment system. It stores carts/orders in memory, has no user authentication or database persistence, and does not process payments. Restarting the server clears orders and carts. Do not use real customer data or deploy it publicly without adding authentication, CSRF protection, robust validation, persistent storage, and secure session handling.

## Product images
- Photos go in `assets/products/<id>.jpg` or `.png`, where `<id>` is the product id in `ShopKart.java`. Each photo has a credit file, `<id>.credit.txt`, which is shown on the product page.
- A product with no photo shows its built-in illustration. Photos fade in once loaded, with a shimmer while they load. Images are lazy-loaded, sized responsively, and cached by the browser for 7 days.
- Run ShopKart from the project folder, so `assets/` is found.

**To fetch photos (needs internet access):**
1. Open `tools/fetch_product_images.py` and replace the contact email in `USER_AGENT`.
2. Run `python tools/fetch_product_images.py`. Use `--force` to re-fetch all products.

The script searches Wikimedia Commons through its public API. It needs no API key and keeps only images licensed for commercial reuse (CC0, public domain, CC BY, CC BY-SA). Non-commercial licences are skipped. Matching is automatic by product name, so check the credit line on each product page; a few matches may be generic rather than the exact brand.

**New products:** add a `new Product(...)` line in `ShopKart.java`, then rerun the script. Existing photos are skipped.

**Limitations:** the product data has no brand or model fields yet, so matching uses the product name only. Adding `brand` and `model` fields would improve matches.
