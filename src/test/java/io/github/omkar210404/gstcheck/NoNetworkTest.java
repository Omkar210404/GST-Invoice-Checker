package io.github.omkar210404.gstcheck;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The tool promises never to contact another machine. This test fails the build if that promise is
 * at risk.
 *
 * <p>The rule has two parts. Everywhere outside the {@code ui} package, no networking class may be
 * mentioned at all. Inside the {@code ui} package, which serves the local upload page, listening on
 * the loopback address is allowed and anything that could open an outbound connection is not.
 */
class NoNetworkTest {

  private static final Path MAIN = Path.of("src", "main", "java");
  private static final String UI_PACKAGE = Path.of("gstcheck", "ui").toString();

  /** Not allowed anywhere outside the ui package. */
  private static final List<String> FORBIDDEN_OUTSIDE_UI =
      List.of(
          "java.net.",
          "javax.net.",
          "com.sun.net.",
          "java.nio.channels.Socket",
          "HttpClient",
          "HttpURLConnection",
          "URLConnection",
          "openStream(",
          "ServerSocket",
          "DatagramSocket",
          "InetAddress");

  /** Ways to make an outbound connection. Not allowed anywhere, the ui package included. */
  private static final List<String> OUTBOUND =
      List.of(
          "HttpClient",
          "HttpURLConnection",
          "URLConnection",
          "openConnection(",
          "openStream(",
          "new Socket(",
          "SocketChannel",
          "DatagramSocket",
          "new URL(",
          "getByName(",
          "getAllByName(");

  @Test
  void codeOutsideTheUiPackageDoesNotReferToNetworkingAtAll() throws IOException {
    List<Path> sources =
        files(MAIN, ".java").stream()
            .filter(path -> !path.toString().contains(UI_PACKAGE))
            .toList();
    assertTrue(sources.size() > 20, "expected to find the production sources");
    for (Path source : sources) {
      String text = Files.readString(source, StandardCharsets.UTF_8);
      for (String forbidden : FORBIDDEN_OUTSIDE_UI) {
        assertFalse(text.contains(forbidden), source + " mentions " + forbidden);
      }
    }
  }

  @Test
  void noProductionCodeCanOpenAnOutboundConnection() throws IOException {
    for (Path source : files(MAIN, ".java")) {
      String text = Files.readString(source, StandardCharsets.UTF_8);
      for (String forbidden : OUTBOUND) {
        assertFalse(text.contains(forbidden), source + " mentions " + forbidden);
      }
    }
  }

  @Test
  void theLocalPageServerBindsToTheLoopbackAddressAndNothingElse() throws IOException {
    String server =
        Files.readString(
            MAIN.resolve(
                Path.of("io", "github", "omkar210404", "gstcheck", "ui", "LocalServer.java")),
            StandardCharsets.UTF_8);
    assertTrue(server.contains("InetAddress.getLoopbackAddress()"));
    // The only socket address created is the loopback one. A bare "new InetSocketAddress(port)"
    // would listen on every network interface.
    assertTrue(server.contains("new InetSocketAddress(InetAddress.getLoopbackAddress(), port)"));
    assertFalse(server.contains("new InetSocketAddress(port"));
    assertFalse(server.contains("0.0.0.0"));
    assertFalse(server.contains("getLocalHost"));
  }

  @Test
  void theReportTemplateLoadsNothingFromAnywhere() throws IOException {
    String text = resource("report", "report-template.html");
    for (String forbidden :
        List.of(
            "http://",
            "https://",
            "//cdn",
            "@import",
            "url(",
            "fetch(",
            "XMLHttpRequest",
            "WebSocket",
            "sendBeacon",
            "<link",
            "<iframe",
            "src=")) {
      assertFalse(text.contains(forbidden), "report template contains " + forbidden);
    }
    assertTrue(text.contains("default-src 'none'"));
  }

  @Test
  void theUploadPageOnlyTalksToTheServerThatServedIt() throws IOException {
    String text = resource("ui", "index.html");
    for (String forbidden :
        List.of(
            "http://",
            "https://",
            "//cdn",
            "@import",
            "url(",
            "XMLHttpRequest",
            "WebSocket",
            "sendBeacon",
            "<link",
            "<script src",
            "<img")) {
      assertFalse(text.contains(forbidden), "upload page contains " + forbidden);
    }
    // Every request the page makes goes to a path on its own origin.
    assertTrue(text.contains("post('/api/check'"));
    assertTrue(text.contains("post('/api/gstin'"));
    assertTrue(text.contains("getText('/api/sample')"));
    assertTrue(text.contains("getText('/api/example-config')"));
  }

  private static String resource(String folder, String name) throws IOException {
    return Files.readString(
        Path.of("src", "main", "resources", folder, name), StandardCharsets.UTF_8);
  }

  private static List<Path> files(Path root, String suffix) throws IOException {
    try (Stream<Path> walk = Files.walk(root)) {
      return walk.filter(path -> path.toString().endsWith(suffix)).toList();
    }
  }
}
