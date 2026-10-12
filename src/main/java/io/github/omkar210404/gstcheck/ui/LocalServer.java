package io.github.omkar210404.gstcheck.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.omkar210404.gstcheck.config.CheckerConfig;
import io.github.omkar210404.gstcheck.config.ConfigLoader;
import io.github.omkar210404.gstcheck.csvio.InvoiceCsvReader;
import io.github.omkar210404.gstcheck.gstin.GstinExplanation;
import io.github.omkar210404.gstcheck.gstin.GstinResult;
import io.github.omkar210404.gstcheck.gstin.GstinValidator;
import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.InputProblemException;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import io.github.omkar210404.gstcheck.report.HtmlReport;
import io.github.omkar210404.gstcheck.report.IssuesCsvReport;
import io.github.omkar210404.gstcheck.report.JsonReport;
import io.github.omkar210404.gstcheck.report.Notices;
import io.github.omkar210404.gstcheck.report.ReportBuilder;
import io.github.omkar210404.gstcheck.report.ReportData;
import io.github.omkar210404.gstcheck.rules.RuleContext;
import io.github.omkar210404.gstcheck.rules.RuleEngine;
import io.github.omkar210404.gstcheck.sample.SampleDataGenerator;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The local page behind the {@code ui} command: pick a CSV in the browser, press a button, see the
 * report.
 *
 * <p>This is the only class in the tool that touches networking, and it does so in one narrow way.
 * It listens on the loopback address only, so it is reachable from this computer and nowhere else,
 * and it never makes an outbound connection. Uploaded data is checked in memory, never written to
 * disk, and discarded when the process stops.
 *
 * <p>Because a web page on another site could still try to reach a loopback port through the user's
 * browser, every request must carry the expected {@code Host} header, and every request that does
 * work must carry a random token that only the page served from here knows.
 */
public final class LocalServer implements AutoCloseable {

  public static final int DEFAULT_PORT = 8765;

  /** Uploads larger than this are refused. */
  static final int MAX_BODY_BYTES = 64 * 1024 * 1024;

  /** How many finished reports are kept in memory for viewing and download. */
  static final int KEPT_RESULTS = 8;

  static final String TOKEN_HEADER = "X-GSTC-Token";

  private static final String PAGE_RESOURCE = "/ui/index.html";
  private static final ObjectMapper JSON =
      new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final HttpServer server;
  private final ExecutorService executor;
  private final Clock clock;
  private final String version;
  private final String token = randomId();
  private final Map<String, StoredResult> results = new LinkedHashMap<>();
  private final CountDownLatch stopped = new CountDownLatch(1);

  /** The three report files for one finished check, held in memory. */
  private record StoredResult(String html, String json, String issuesCsv) {}

  /** A request that should be answered with an HTTP error status. */
  private static final class HttpError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int status;

    HttpError(int status, String message) {
      super(message);
      this.status = status;
    }
  }

  private LocalServer(HttpServer server, Clock clock, String version) {
    this.server = server;
    this.clock = clock;
    this.version = version;
    this.executor = Executors.newFixedThreadPool(4);
  }

  /**
   * Starts listening on the loopback address.
   *
   * @param port the port to listen on, or 0 to let the system pick a free one
   * @throws IOException if the port cannot be opened, for example because it is in use
   */
  public static LocalServer start(int port, Clock clock, String version) throws IOException {
    HttpServer http =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
    LocalServer local = new LocalServer(http, clock, version);
    http.createContext("/", local::handle);
    http.setExecutor(local.executor);
    http.start();
    return local;
  }

  public int port() {
    return server.getAddress().getPort();
  }

  /** The address to open in a browser. */
  public String url() {
    return "http://127.0.0.1:" + port() + "/";
  }

  /** True when the server is reachable from this computer only. */
  public boolean boundToLoopback() {
    return server.getAddress().getAddress().isLoopbackAddress();
  }

  /** The token the page must send with every request that does work. */
  String token() {
    return token;
  }

  /** Blocks until {@link #close()} is called. */
  public void awaitStop() throws InterruptedException {
    stopped.await();
  }

  @Override
  public void close() {
    server.stop(0);
    executor.shutdownNow();
    stopped.countDown();
  }

  // --- request handling ---

  private void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      try {
        route(exchange);
      } catch (InputProblemException e) {
        sendError(exchange, 400, e.getMessage());
      } catch (HttpError e) {
        sendError(exchange, e.status, e.getMessage());
      } catch (RuntimeException e) {
        sendError(exchange, 500, "Unexpected error: " + e);
      }
    }
  }

  private void route(HttpExchange exchange) throws IOException {
    if (!isAllowedHost(exchange.getRequestHeaders().getFirst("Host"), port())) {
      throw new HttpError(403, "This page only answers requests addressed to this computer.");
    }
    String method = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getPath();
    switch (path) {
      case "/" -> {
        require(method, "GET");
        sendPage(exchange);
      }
      case "/api/example-config" -> {
        require(method, "GET");
        send(exchange, 200, "text/plain; charset=utf-8", ConfigLoader.exampleText());
      }
      case "/api/sample" -> {
        require(method, "GET");
        send(
            exchange,
            200,
            "text/plain; charset=utf-8",
            SampleDataGenerator.generate(SampleDataGenerator.DEFAULT_SEED).invoicesCsv());
      }
      case "/api/check" -> {
        require(method, "POST");
        requireToken(exchange);
        sendJson(exchange, 200, check(readJson(exchange)));
      }
      case "/api/gstin" -> {
        require(method, "POST");
        requireToken(exchange);
        sendJson(exchange, 200, gstin(readJson(exchange)));
      }
      default -> {
        if (!path.startsWith("/result/")) {
          throw new HttpError(404, "Not found.");
        }
        require(method, "GET");
        sendResult(exchange, path);
      }
    }
  }

  /**
   * True when the Host header names this computer and this port. A request that reached the
   * loopback port under any other name was sent there by a page from somewhere else.
   */
  static boolean isAllowedHost(String host, int port) {
    return host != null
        && (host.equals("127.0.0.1:" + port)
            || host.equalsIgnoreCase("localhost:" + port)
            || host.equals("[::1]:" + port));
  }

  private static void require(String method, String expected) {
    if (!expected.equals(method)) {
      throw new HttpError(405, "Use " + expected + " for this address.");
    }
  }

  private void requireToken(HttpExchange exchange) {
    String sent = exchange.getRequestHeaders().getFirst(TOKEN_HEADER);
    if (sent == null || !constantTimeEquals(sent, token)) {
      throw new HttpError(403, "Missing or wrong page token. Reload the page and try again.");
    }
  }

  private static boolean constantTimeEquals(String first, String second) {
    return java.security.MessageDigest.isEqual(
        first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
  }

  // --- the two operations ---

  private Map<String, Object> check(JsonNode request) {
    String csv = text(request, "csv");
    if (csv.isBlank()) {
      throw new InputProblemException("Choose a CSV file first. The file you picked is empty.");
    }
    String fileName = displayName(text(request, "file_name"));
    CheckerConfig config = config(text(request, "config"));
    LocalDate asOf = asOf(text(request, "as_of"));

    List<InvoiceLine> lines = new InvoiceCsvReader(config).read(new StringReader(csv), fileName);
    List<Issue> issues = RuleEngine.standard().run(lines, new RuleContext(config, asOf));
    ReportData report =
        ReportBuilder.build(
            new ReportBuilder.RunInfo(
                version, asOf, fileName, "the Config box", config.financialYear().label()),
            lines,
            issues);

    String id = randomId();
    store(
        id,
        new StoredResult(
            HtmlReport.render(report), JsonReport.render(report), IssuesCsvReport.render(report)));

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("id", id);
    response.put("file_name", fileName);
    response.put("as_of", asOf.toString());
    response.put("financial_year", report.financialYear());
    response.put("passed", report.summary().errors() == 0);
    response.put("summary", report.summary());
    response.put("check_counts", report.checkCounts());
    return response;
  }

  private Map<String, Object> gstin(JsonNode request) {
    CheckerConfig config = config(text(request, "config"));
    GstinResult result = new GstinValidator(config.stateCodes()).validate(text(request, "gstin"));
    String value = result.normalized();

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("gstin", value.length() > 60 ? value.substring(0, 60) : value);
    response.put("structurally_valid", result.structurallyValid());
    if (!result.has(CheckId.GSTIN_001)) {
      String state = value.substring(0, 2);
      Map<String, String> parts = new LinkedHashMap<>();
      parts.put("state_code", state);
      parts.put("state_name", config.isKnownState(state) ? config.states().get(state).name() : "");
      parts.put("pan_part", value.substring(2, 12));
      parts.put("entity_number", value.substring(12, 13));
      parts.put("fixed_character", value.substring(13, 14));
      parts.put("check_character", value.substring(14, 15));
      response.put("parts", parts);
    }
    List<Map<String, String>> steps = new ArrayList<>();
    for (GstinExplanation.Step step : GstinExplanation.steps(result)) {
      Map<String, String> row = new LinkedHashMap<>();
      row.put("check_id", step.check().id());
      row.put("label", step.label());
      row.put("status", step.status().name());
      row.put("message", step.message());
      row.put("suggested_fix", step.suggestedFix());
      steps.add(row);
    }
    response.put("steps", steps);
    response.put("notice", GstinExplanation.notice(result));
    return response;
  }

  private static CheckerConfig config(String yaml) {
    if (yaml.isBlank()) {
      throw new InputProblemException(
          "The Config box is empty. Press 'Reset to example' or load your own YAML file.");
    }
    return ConfigLoader.parse(yaml, "in the Config box");
  }

  private LocalDate asOf(String text) {
    if (text.isBlank()) {
      return LocalDate.now(clock);
    }
    try {
      return LocalDate.parse(text.strip());
    } catch (DateTimeParseException e) {
      throw new InputProblemException(
          "The 'as of' date '" + text + "' is not valid. Use the form 2026-10-12.", e);
    }
  }

  /** A short, printable name for the uploaded file: no folders and no control characters. */
  static String displayName(String name) {
    String base = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    StringBuilder clean = new StringBuilder();
    base.codePoints()
        .limit(120)
        .filter(c -> !Character.isISOControl(c))
        .forEach(clean::appendCodePoint);
    String result = clean.toString().strip();
    return result.isEmpty() ? "uploaded.csv" : result;
  }

  private void store(String id, StoredResult result) {
    synchronized (results) {
      results.put(id, result);
      Iterator<String> oldest = results.keySet().iterator();
      while (results.size() > KEPT_RESULTS && oldest.hasNext()) {
        oldest.next();
        oldest.remove();
      }
    }
  }

  // --- responses ---

  private void sendPage(HttpExchange exchange) throws IOException {
    String page =
        HtmlReport.fill(
            PAGE_RESOURCE,
            Map.of(
                "TOKEN", HtmlReport.escapeHtml(token),
                "TOOL_VERSION", HtmlReport.escapeHtml(version),
                "TODAY", HtmlReport.escapeHtml(LocalDate.now(clock).toString()),
                "STRUCTURAL_NOTICE", HtmlReport.escapeHtml(Notices.STRUCTURAL_ONLY),
                "DISCLAIMER", HtmlReport.escapeHtml(Notices.DISCLAIMER)));
    Headers headers = exchange.getResponseHeaders();
    headers.set(
        "Content-Security-Policy",
        "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline';"
            + " connect-src 'self'; frame-src 'self'; base-uri 'none'; form-action 'none'");
    headers.set("X-Frame-Options", "DENY");
    send(exchange, 200, "text/html; charset=utf-8", page);
  }

  private void sendResult(HttpExchange exchange, String path) throws IOException {
    String[] parts = path.split("/");
    // ["", "result", id, file]
    if (parts.length != 4) {
      throw new HttpError(404, "Not found.");
    }
    StoredResult result;
    synchronized (results) {
      result = results.get(parts[2]);
    }
    if (result == null) {
      throw new HttpError(
          404, "That report is no longer in memory. Run the check again to recreate it.");
    }
    Headers headers = exchange.getResponseHeaders();
    switch (parts[3]) {
      case "report.html" -> {
        // The report is shown inside the page, so it may be framed by this origin only.
        headers.set("X-Frame-Options", "SAMEORIGIN");
        send(exchange, 200, "text/html; charset=utf-8", result.html());
      }
      case "download-report.html" -> {
        headers.set("Content-Disposition", "attachment; filename=\"report.html\"");
        send(exchange, 200, "text/html; charset=utf-8", result.html());
      }
      case "report.json" -> {
        headers.set("Content-Disposition", "attachment; filename=\"report.json\"");
        send(exchange, 200, "application/json; charset=utf-8", result.json());
      }
      case "issues.csv" -> {
        headers.set("Content-Disposition", "attachment; filename=\"issues.csv\"");
        send(exchange, 200, "text/csv; charset=utf-8", result.issuesCsv());
      }
      default -> throw new HttpError(404, "Not found.");
    }
  }

  private static JsonNode readJson(HttpExchange exchange) throws IOException {
    String length = exchange.getRequestHeaders().getFirst("Content-Length");
    if (length != null && length.matches("\\d{1,18}") && Long.parseLong(length) > MAX_BODY_BYTES) {
      throw tooLarge();
    }
    byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
    if (body.length > MAX_BODY_BYTES) {
      throw tooLarge();
    }
    try {
      JsonNode node = JSON.readTree(body);
      if (node == null || !node.isObject()) {
        throw new HttpError(400, "The request was not understood. Reload the page and try again.");
      }
      return node;
    } catch (JsonProcessingException e) {
      throw new HttpError(400, "The request was not understood. Reload the page and try again.");
    }
  }

  private static HttpError tooLarge() {
    return new HttpError(
        413,
        "That file is too large for the page (limit "
            + MAX_BODY_BYTES / (1024 * 1024)
            + " MB). Use the 'check' command for big files.");
  }

  private static String text(JsonNode request, String field) {
    JsonNode value = request.get(field);
    return value == null || value.isNull() ? "" : value.asText("");
  }

  private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
    send(exchange, status, "application/json; charset=utf-8", JSON.writeValueAsString(body));
  }

  private static void sendError(HttpExchange exchange, int status, String message)
      throws IOException {
    sendJson(exchange, status, Map.of("error", message == null ? "Error" : message));
  }

  private static void send(HttpExchange exchange, int status, String contentType, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    Headers headers = exchange.getResponseHeaders();
    headers.set("Content-Type", contentType);
    headers.set("Cache-Control", "no-store");
    headers.set("X-Content-Type-Options", "nosniff");
    headers.set("Referrer-Policy", "no-referrer");
    exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) {
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    }
  }

  private static String randomId() {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
