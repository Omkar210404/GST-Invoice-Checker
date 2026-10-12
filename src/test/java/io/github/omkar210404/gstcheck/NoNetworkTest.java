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
 * The tool promises to work fully offline. This test fails the build if production code starts
 * referring to networking classes, or if a bundled resource starts pointing at a URL.
 */
class NoNetworkTest {

  private static final List<String> FORBIDDEN_IN_CODE =
      List.of(
          "java.net.",
          "javax.net.",
          "java.nio.channels.Socket",
          "HttpClient",
          "HttpURLConnection",
          "URLConnection",
          "openStream(",
          "ServerSocket",
          "DatagramSocket",
          "InetAddress");

  @Test
  void productionCodeDoesNotReferToNetworkingClasses() throws IOException {
    List<Path> sources = files(Path.of("src", "main", "java"), ".java");
    assertTrue(sources.size() > 20, "expected to find the production sources");
    for (Path source : sources) {
      String text = Files.readString(source, StandardCharsets.UTF_8);
      for (String forbidden : FORBIDDEN_IN_CODE) {
        assertFalse(text.contains(forbidden), source + " mentions " + forbidden);
      }
    }
  }

  @Test
  void theHtmlTemplateLoadsNothingFromAnywhere() throws IOException {
    Path template = Path.of("src", "main", "resources", "report", "report-template.html");
    String text = Files.readString(template, StandardCharsets.UTF_8);
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
      assertFalse(text.contains(forbidden), "template contains " + forbidden);
    }
    assertTrue(text.contains("default-src 'none'"));
  }

  private static List<Path> files(Path root, String suffix) throws IOException {
    try (Stream<Path> walk = Files.walk(root)) {
      return walk.filter(path -> path.toString().endsWith(suffix)).toList();
    }
  }
}
