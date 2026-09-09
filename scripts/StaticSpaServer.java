import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

public final class StaticSpaServer {
  private static final Set<String> HOP_HEADERS = Set.of(
      "connection", "content-length", "host", "keep-alive", "proxy-authenticate",
      "proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade");
  private static final HttpClient HTTP = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5)).build();
  private static Path webRoot;
  private static URI backend;

  public static void main(String[] args) throws Exception {
    if (args.length != 3) {
      throw new IllegalArgumentException("Usage: StaticSpaServer <dist> <port> <backend-url>");
    }
    webRoot = Path.of(args[0]).toAbsolutePath().normalize();
    int port = Integer.parseInt(args[1]);
    backend = URI.create(args[2]);
    HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
    server.createContext("/", StaticSpaServer::handle);
    server.setExecutor(Executors.newFixedThreadPool(12));
    server.start();
    System.out.println("JiaBei SPA server listening on http://127.0.0.1:" + port);
  }

  private static void handle(HttpExchange exchange) throws IOException {
    try {
      String path = exchange.getRequestURI().getPath();
      if (path.startsWith("/api/")) proxy(exchange);
      else serveStatic(exchange, path);
    } catch (Exception error) {
      byte[] body = ("Internal gateway error: " + error.getMessage()).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
      exchange.sendResponseHeaders(502, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    }
  }

  private static void proxy(HttpExchange exchange) throws Exception {
    URI target = backend.resolve(exchange.getRequestURI().toString());
    byte[] requestBody = exchange.getRequestBody().readAllBytes();
    HttpRequest.BodyPublisher publisher = requestBody.length == 0
        ? HttpRequest.BodyPublishers.noBody()
        : HttpRequest.BodyPublishers.ofByteArray(requestBody);
    HttpRequest.Builder builder = HttpRequest.newBuilder(target)
        .timeout(Duration.ofSeconds(30)).method(exchange.getRequestMethod(), publisher);
    exchange.getRequestHeaders().forEach((name, values) -> {
      if (!HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
        values.forEach(value -> builder.header(name, value));
      }
    });
    HttpResponse<byte[]> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    Headers outgoing = exchange.getResponseHeaders();
    response.headers().map().forEach((name, values) -> {
      if (!HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT))) outgoing.put(name, values);
    });
    exchange.sendResponseHeaders(response.statusCode(), response.body().length);
    exchange.getResponseBody().write(response.body());
    exchange.close();
  }

  private static void serveStatic(HttpExchange exchange, String requestPath) throws IOException {
    String decoded = URLDecoder.decode(requestPath, StandardCharsets.UTF_8);
    String relative = decoded.startsWith("/") ? decoded.substring(1) : decoded;
    Path candidate = webRoot.resolve(relative).normalize();
    if (!candidate.startsWith(webRoot) || !Files.isRegularFile(candidate)) candidate = webRoot.resolve("index.html");
    byte[] body = Files.readAllBytes(candidate);
    exchange.getResponseHeaders().set("Content-Type", mime(candidate));
    exchange.getResponseHeaders().set("Cache-Control", candidate.getFileName().toString().equals("index.html")
        ? "no-cache" : "public, max-age=31536000, immutable");
    exchange.sendResponseHeaders(200, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }

  private static String mime(Path file) {
    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
    if (name.endsWith(".html")) return "text/html; charset=utf-8";
    if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
    if (name.endsWith(".css")) return "text/css; charset=utf-8";
    if (name.endsWith(".svg")) return "image/svg+xml";
    if (name.endsWith(".png")) return "image/png";
    if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
    return "application/octet-stream";
  }
}

