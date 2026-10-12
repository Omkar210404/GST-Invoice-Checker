package io.github.omkar210404.gstcheck.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

/** The full report as JSON, for scripts and CI. Property names are snake_case. */
public final class JsonReport {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

  private JsonReport() {}

  /** Pretty-printed JSON with {@code \n} line endings on every platform. */
  public static String render(ReportData data) {
    DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
    DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
    printer.indentObjectsWith(indenter);
    printer.indentArraysWith(indenter);
    try {
      return MAPPER.writer(printer).writeValueAsString(data) + "\n";
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not write the JSON report", e);
    }
  }

  /** Single-line JSON, used for embedding in the HTML report. */
  static String renderCompact(ReportData data) {
    try {
      return MAPPER.writeValueAsString(data);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not write the JSON report", e);
    }
  }
}
