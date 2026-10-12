package io.github.omkar210404.gstcheck.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.omkar210404.gstcheck.config.ConfigLoader;
import io.github.omkar210404.gstcheck.sample.SampleDataGenerator;
import io.github.omkar210404.gstcheck.testutil.Fixtures;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Drives the local page over real HTTP on a free loopback port. */
class LocalServerTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-12T06:00:00Z"), ZoneOffset.UTC);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String SAMPLE_CSV =
      SampleDataGenerator.generate(SampleDataGenerator.DEFAULT_SEED).invoicesCsv();

  private final HttpClient client = HttpClient.newHttpClient();
  private LocalServer server;

  @BeforeEach
  void start() throws IOException {
    server = LocalServer.start(0, CLOCK, "TEST");
  }

  @AfterEach
  void stop() {
    server.close();
  }

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    return client.send(
        HttpRequest.newBuilder(URI.create(server.url() + path.substring(1))).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String path, String body, String token)
      throws IOException, InterruptedException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(server.url() + path.substring(1)))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (token != null) {
      request.header(LocalServer.TOKEN_HEADER, token);
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String checkRequest(String csv, String config, String asOf) {
    ObjectNode node = JSON.createObjectNode();
    node.put("file_name", "C:\\Users\\someone\\invoices.csv");
    node.put("csv", csv);
    node.put("config", config);
    node.put("as_of", asOf);
    return node.toString();
  }

  private JsonNode json(HttpResponse<String> response) throws IOException {
    return JSON.readTree(response.body());
  }

  // --- where it listens ---

  @Test
  void listensOnTheLoopbackAddressOnly() {
    assertTrue(server.boundToLoopback());
    assertTrue(server.url().startsWith("http://127.0.0.1:"));
    assertTrue(server.port() > 0);
  }

  @Test
  void onlyRequestsAddressedToThisComputerAreAllowed() {
    assertTrue(LocalServer.isAllowedHost("127.0.0.1:8765", 8765));
    assertTrue(LocalServer.isAllowedHost("localhost:8765", 8765));
    assertTrue(LocalServer.isAllowedHost("LOCALHOST:8765", 8765));
    assertTrue(LocalServer.isAllowedHost("[::1]:8765", 8765));
    // A page elsewhere that resolves its own name to 127.0.0.1 still sends its own Host header.
    assertFalse(LocalServer.isAllowedHost("evil.example:8765", 8765));
    assertFalse(LocalServer.isAllowedHost("127.0.0.1:9999", 8765));
    assertFalse(LocalServer.isAllowedHost("127.0.0.1", 8765));
    assertFalse(LocalServer.isAllowedHost("192.168.1.20:8765", 8765));
    assertFalse(LocalServer.isAllowedHost(null, 8765));
  }

  // --- the page ---

  @Test
  void servesThePageWithTheUploadControlsAndBothNotices() throws Exception {
    HttpResponse<String> response = get("/");
    assertEquals(200, response.statusCode());
    String page = response.body();
    assertTrue(page.contains("type=\"file\""), "there is a file input");
    assertTrue(page.contains("Check this file"));
    assertTrue(page.contains("Try the synthetic sample"));
    assertTrue(page.contains("Check GSTIN"));
    assertTrue(page.contains("not tax advice"));
    assertTrue(page.contains("does not prove the GSTIN is registered or active"));
    assertTrue(page.contains(server.token()), "the page carries the token it must send back");
    assertFalse(page.contains("{{"), "every placeholder was filled in");
    assertTrue(page.contains("value=\"2026-10-12\""), "the date defaults to today");
  }

  @Test
  void thePageForbidsLoadingAnythingFromElsewhere() throws Exception {
    HttpResponse<String> response = get("/");
    String policy = response.headers().firstValue("Content-Security-Policy").orElse("");
    assertTrue(policy.contains("default-src 'none'"));
    assertTrue(policy.contains("connect-src 'self'"));
    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
    assertEquals("DENY", response.headers().firstValue("X-Frame-Options").orElse(""));
    assertFalse(response.body().contains("https://"));
    assertFalse(response.body().contains("http://"));
  }

  @Test
  void servesTheExampleConfigAndTheSyntheticSample() throws Exception {
    assertEquals(ConfigLoader.exampleText(), get("/api/example-config").body());
    assertEquals(SAMPLE_CSV, get("/api/sample").body());
  }

  // --- checking a file ---

  @Test
  void checkingTheSampleReturnsCountsAndMakesTheReportsAvailable() throws Exception {
    HttpResponse<String> response =
        post(
            "/api/check",
            checkRequest(SAMPLE_CSV, ConfigLoader.exampleText(), "2026-10-12"),
            server.token());
    assertEquals(200, response.statusCode());
    JsonNode result = json(response);
    assertEquals(48, result.at("/summary/lines").asInt());
    assertEquals(25, result.at("/summary/errors").asInt());
    assertEquals(5, result.at("/summary/warnings").asInt());
    assertFalse(result.get("passed").asBoolean());
    assertEquals("invoices.csv", result.get("file_name").asText(), "folders are stripped");
    assertEquals(29, result.get("check_counts").size());

    String base = "/result/" + result.get("id").asText() + "/";
    HttpResponse<String> html = get(base + "report.html");
    assertEquals(200, html.statusCode());
    assertTrue(html.body().contains("GST invoice check"));
    assertEquals("SAMEORIGIN", html.headers().firstValue("X-Frame-Options").orElse(""));

    HttpResponse<String> csv = get(base + "issues.csv");
    assertTrue(csv.body().startsWith("line_no,invoice_no,"));
    assertTrue(csv.headers().firstValue("Content-Disposition").orElse("").contains("issues.csv"));

    assertTrue(get(base + "report.json").body().contains("\"errors\" : 25"));
    assertEquals(200, get(base + "download-report.html").statusCode());
    assertEquals(404, get(base + "secrets.txt").statusCode());
  }

  @Test
  void aCleanFilePassesAndTheDateDefaultsToTheClock() throws Exception {
    String csv = SAMPLE_CSV.lines().limit(18).reduce("", (a, b) -> a + b + "\n");
    JsonNode result =
        json(post("/api/check", checkRequest(csv, ConfigLoader.exampleText(), ""), server.token()));
    assertTrue(result.get("passed").asBoolean());
    assertEquals(17, result.at("/summary/lines").asInt());
    assertEquals("2026-10-12", result.get("as_of").asText());
  }

  @Test
  void theConfigTypedIntoThePageIsTheOneUsed() throws Exception {
    // With 18 removed from the allowed rates, lines at 18% must raise RATE-001.
    String config =
        ConfigLoader.exampleText()
            .replace("[0, 0.25, 3, 5, 12, 18, 28, 40]", "[0, 0.25, 3, 5, 12, 28, 40]");
    assertNotEquals(ConfigLoader.exampleText(), config);
    String csv = SAMPLE_CSV.lines().limit(18).reduce("", (a, b) -> a + b + "\n");
    JsonNode result = json(post("/api/check", checkRequest(csv, config, ""), server.token()));
    assertFalse(result.get("passed").asBoolean());
    assertEquals("RATE-001", result.at("/check_counts/0/check_id").asText());
  }

  @Test
  void problemsWithTheUploadComeBackAsReadableErrors() throws Exception {
    String config = ConfigLoader.exampleText();
    String token = server.token();

    HttpResponse<String> empty = post("/api/check", checkRequest("", config, ""), token);
    assertEquals(400, empty.statusCode());
    assertTrue(json(empty).get("error").asText().contains("Choose a CSV file"));

    HttpResponse<String> notCsv =
        post("/api/check", checkRequest("hello\nworld\n", config, ""), token);
    assertEquals(400, notCsv.statusCode());
    assertTrue(json(notCsv).get("error").asText().contains("missing required column"));

    HttpResponse<String> badConfig =
        post("/api/check", checkRequest(SAMPLE_CSV, "financial_year: \"2026-27\"\n", ""), token);
    assertEquals(400, badConfig.statusCode());
    assertTrue(json(badConfig).get("error").asText().contains("rates.allowed_slabs is required"));

    HttpResponse<String> noConfig = post("/api/check", checkRequest(SAMPLE_CSV, "  ", ""), token);
    assertEquals(400, noConfig.statusCode());
    assertTrue(json(noConfig).get("error").asText().contains("Config box is empty"));

    HttpResponse<String> badDate =
        post("/api/check", checkRequest(SAMPLE_CSV, config, "12/10/2026"), token);
    assertEquals(400, badDate.statusCode());
    assertTrue(json(badDate).get("error").asText().contains("not valid"));

    assertEquals(400, post("/api/check", "this is not json", token).statusCode());
    assertEquals(400, post("/api/check", "[1, 2]", token).statusCode());
  }

  // --- protection against other pages using the port ---

  @Test
  void requestsWithoutTheTokenAreRefused() throws Exception {
    String body = checkRequest(SAMPLE_CSV, ConfigLoader.exampleText(), "");
    assertEquals(403, post("/api/check", body, null).statusCode());
    assertEquals(403, post("/api/check", body, "0".repeat(32)).statusCode());
    assertEquals(403, post("/api/gstin", "{\"gstin\":\"x\"}", null).statusCode());
  }

  @Test
  void reportIdsCannotBeGuessed() throws Exception {
    assertEquals(404, get("/result/00000000000000000000000000000000/report.html").statusCode());
    assertEquals(404, get("/result/x").statusCode());
    assertEquals(404, get("/result/../../etc/passwd").statusCode());
  }

  @Test
  void onlyTheNewestReportsAreKeptInMemory() throws Exception {
    String csv = SAMPLE_CSV.lines().limit(3).reduce("", (a, b) -> a + b + "\n");
    String body = checkRequest(csv, ConfigLoader.exampleText(), "");
    String first = json(post("/api/check", body, server.token())).get("id").asText();
    for (int i = 0; i < LocalServer.KEPT_RESULTS; i++) {
      post("/api/check", body, server.token());
    }
    assertEquals(404, get("/result/" + first + "/report.html").statusCode());
  }

  @Test
  void wrongMethodsAndUnknownAddressesAreRefused() throws Exception {
    assertEquals(405, get("/api/check").statusCode());
    assertEquals(405, post("/", "{}", server.token()).statusCode());
    assertEquals(404, get("/nope").statusCode());
    assertEquals(404, get("/api/").statusCode());
  }

  // --- single GSTIN ---

  @Test
  void aValidGstinIsExplainedPartByPart() throws Exception {
    ObjectNode request = JSON.createObjectNode();
    request.put("gstin", " " + Fixtures.SUPPLIER_27.toLowerCase() + " ");
    request.put("config", ConfigLoader.exampleText());
    JsonNode result = json(post("/api/gstin", request.toString(), server.token()));
    assertTrue(result.get("structurally_valid").asBoolean());
    assertEquals(Fixtures.SUPPLIER_27, result.get("gstin").asText());
    assertEquals("27", result.at("/parts/state_code").asText());
    assertEquals("Maharashtra", result.at("/parts/state_name").asText());
    assertEquals(7, result.get("steps").size());
    assertEquals("WARN", result.at("/steps/6/status").asText(), "lower case is only a warning");
    assertTrue(result.get("notice").asText().contains("does not prove"));
  }

  @Test
  void anInvalidGstinSaysWhichCheckFailedAndHowToFixIt() throws Exception {
    String wrong =
        Fixtures.SUPPLIER_27.substring(0, 14)
            + (Fixtures.SUPPLIER_27.charAt(14) == 'A' ? 'B' : 'A');
    ObjectNode request = JSON.createObjectNode();
    request.put("gstin", wrong);
    request.put("config", ConfigLoader.exampleText());
    JsonNode result = json(post("/api/gstin", request.toString(), server.token()));
    assertFalse(result.get("structurally_valid").asBoolean());
    assertEquals("GSTIN-006", result.at("/steps/5/check_id").asText());
    assertEquals("FAIL", result.at("/steps/5/status").asText());
    assertFalse(result.at("/steps/5/suggested_fix").asText().isBlank());
    assertTrue(result.get("notice").asText().contains("malformed"));
  }

  @Test
  void aGstinOfTheWrongLengthHasNoPartsAndSkipsPositionalChecks() throws Exception {
    ObjectNode request = JSON.createObjectNode();
    request.put("gstin", "27ABC");
    request.put("config", ConfigLoader.exampleText());
    JsonNode result = json(post("/api/gstin", request.toString(), server.token()));
    assertFalse(result.has("parts"));
    assertEquals("FAIL", result.at("/steps/0/status").asText());
    assertEquals("SKIP", result.at("/steps/5/status").asText());
  }

  // --- small helpers ---

  @Test
  void uploadedFileNamesAreReducedToASafeDisplayName() {
    assertEquals("invoices.csv", LocalServer.displayName("C:\\Users\\me\\invoices.csv"));
    assertEquals("invoices.csv", LocalServer.displayName("/home/me/invoices.csv"));
    assertEquals("ab.csv", LocalServer.displayName("a\u001B\nb.csv"));
    assertEquals("uploaded.csv", LocalServer.displayName(""));
    assertEquals("uploaded.csv", LocalServer.displayName("folder/"));
    assertEquals(120, LocalServer.displayName("x".repeat(500)).length());
  }

  @Test
  void closingStopsTheServerAndReleasesAnyoneWaiting() throws Exception {
    LocalServer other = LocalServer.start(0, CLOCK, "TEST");
    Thread waiter =
        new Thread(
            () -> {
              try {
                other.awaitStop();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });
    waiter.start();
    other.close();
    waiter.join(5000);
    assertFalse(waiter.isAlive());
  }
}
