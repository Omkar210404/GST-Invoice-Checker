package io.github.omkar210404.gstcheck.report;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The self-contained HTML report: one file with inline CSS and JavaScript, no external resources
 * and no network access.
 *
 * <p>The template approach is deliberately small. The page is a resource file with a handful of
 * {@code {{PLACEHOLDER}}} markers. Header text is HTML-escaped into the page; the report data is
 * embedded as JSON and drawn by the page's script using {@code textContent}, so text from the input
 * CSV is never interpreted as markup.
 */
public final class HtmlReport {

  private static final String TEMPLATE_RESOURCE = "/report/report-template.html";
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Z_]+)}}");

  private HtmlReport() {}

  public static String render(ReportData data) {
    Map<String, String> values =
        Map.of(
            "INPUT_FILE", escapeHtml(data.inputFile()),
            "CONFIG_FILE", escapeHtml(data.configFile()),
            "TOOL", escapeHtml(data.tool()),
            "TOOL_VERSION", escapeHtml(data.toolVersion()),
            "AS_OF", escapeHtml(data.asOf()),
            "FINANCIAL_YEAR", escapeHtml(data.financialYear()),
            "STRUCTURAL_NOTICE", escapeHtml(data.structuralNotice()),
            "DISCLAIMER", escapeHtml(data.disclaimer()),
            "REPORT_JSON", escapeJsonForScript(JsonReport.renderCompact(data)));

    return fill(TEMPLATE_RESOURCE, values);
  }

  /**
   * Loads a template from the classpath and fills in its {@code {{PLACEHOLDER}}} markers. Values
   * are inserted as given, so the caller must escape them for where they land.
   *
   * @throws IllegalStateException if the template is missing or names an unknown placeholder
   */
  public static String fill(String resource, Map<String, String> values) {
    // One pass over the template, so a value that itself contains "{{...}}" is not expanded again.
    Matcher matcher = PLACEHOLDER.matcher(template(resource));
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String value = values.get(matcher.group(1));
      if (value == null) {
        throw new IllegalStateException("Unknown placeholder in template: " + matcher.group());
      }
      matcher.appendReplacement(out, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private static String template(String resource) {
    try (InputStream in = HtmlReport.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Missing resource " + resource);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    } catch (IOException e) {
      throw new IllegalStateException("Could not read resource " + resource, e);
    }
  }

  /** Escapes text for use in HTML content or a quoted attribute. */
  public static String escapeHtml(String text) {
    StringBuilder out = new StringBuilder(text.length() + 16);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        case '\'' -> out.append("&#39;");
        default -> out.append(c);
      }
    }
    return out.toString();
  }

  /**
   * Makes JSON safe to place inside a {@code <script>} element. In JSON these characters can only
   * occur inside strings, so replacing them with unicode escapes keeps the JSON equivalent while
   * making it impossible for input data to close the script element or open a comment.
   */
  static String escapeJsonForScript(String json) {
    StringBuilder out = new StringBuilder(json.length() + 64);
    for (int i = 0; i < json.length(); i++) {
      char c = json.charAt(i);
      switch (c) {
        case '<' -> out.append("\\u003c");
        case '>' -> out.append("\\u003e");
        case '&' -> out.append("\\u0026");
        case ' ' -> out.append("\\u2028");
        case ' ' -> out.append("\\u2029");
        default -> out.append(c);
      }
    }
    return out.toString();
  }
}
