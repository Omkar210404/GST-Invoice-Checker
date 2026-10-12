package io.github.omkar210404.gstcheck.report;

import io.github.omkar210404.gstcheck.report.ReportData.CheckCount;
import io.github.omkar210404.gstcheck.report.ReportData.IssueReport;
import io.github.omkar210404.gstcheck.report.ReportData.Summary;
import java.util.ArrayList;
import java.util.List;

/** The summary printed to the terminal. Plain ASCII, so it reads the same in any console. */
public final class ConsoleReport {

  /** How many issues to list before pointing the reader at the full reports. */
  public static final int DEFAULT_MAX_ISSUES = 15;

  private static final int MESSAGE_WIDTH = 88;

  private ConsoleReport() {}

  public static String render(ReportData data) {
    return render(data, DEFAULT_MAX_ISSUES);
  }

  /** Renders the summary with {@code \n} line endings. */
  public static String render(ReportData data, int maxIssues) {
    StringBuilder out = new StringBuilder();
    Summary summary = data.summary();
    out.append(data.tool()).append(' ').append(data.toolVersion()).append('\n');
    out.append("Input:   ").append(clean(data.inputFile())).append('\n');
    out.append("Config:  ").append(clean(data.configFile())).append('\n');
    out.append("Year:    ").append(data.financialYear());
    out.append("   As of: ").append(data.asOf()).append("\n\n");

    out.append(
        table(
            List.of("Summary", "Count"),
            List.of(
                List.of("Lines checked", String.valueOf(summary.lines())),
                List.of("Invoices", String.valueOf(summary.invoices())),
                List.of("Invoices with errors", String.valueOf(summary.invoicesWithErrors())),
                List.of("Lines with issues", String.valueOf(summary.linesWithIssues())),
                List.of("Errors", String.valueOf(summary.errors())),
                List.of("Warnings", String.valueOf(summary.warnings()))),
            List.of(false, true)));
    out.append('\n');

    if (data.checkCounts().isEmpty()) {
      out.append("No issues found.\n");
    } else {
      List<List<String>> rows = new ArrayList<>();
      for (CheckCount count : data.checkCounts()) {
        rows.add(
            List.of(
                count.checkId(), count.severity(), String.valueOf(count.count()), count.title()));
      }
      out.append(
          table(
              List.of("Check", "Severity", "Count", "What it means"),
              rows,
              List.of(false, false, true, false)));
      out.append('\n');

      int total = data.issues().size();
      int shown = Math.min(Math.max(maxIssues, 0), total);
      if (shown > 0) {
        out.append(
            shown == total
                ? "All " + total + " issue(s):\n"
                : "First " + shown + " of " + total + " issues (the rest are in the reports):\n");
        for (IssueReport issue : data.issues().subList(0, shown)) {
          out.append("  row ")
              .append(issue.lineNo())
              .append("  ")
              .append(issue.checkId())
              .append("  ")
              .append(issue.severity())
              .append("  ")
              .append(
                  issue.invoiceNo().isEmpty() ? "(no invoice number)" : clean(issue.invoiceNo()))
              .append('\n');
          appendWrapped(out, issue.message(), "      ");
        }
        out.append('\n');
      }
    }

    out.append("Result: ");
    if (summary.errors() > 0) {
      out.append("FAILED - ").append(summary.errors()).append(" error(s)");
    } else {
      out.append("PASSED - no errors");
    }
    if (summary.warnings() > 0) {
      out.append(", ").append(summary.warnings()).append(" warning(s)");
    }
    out.append("\n\n");
    appendWrapped(out, "Note: " + data.structuralNotice(), "");
    appendWrapped(out, "Note: " + data.disclaimer(), "");
    return out.toString();
  }

  /** Replaces control characters, so text from the input file cannot send escape codes. */
  static String clean(String text) {
    StringBuilder clean = new StringBuilder(text.length());
    text.codePoints().map(c -> Character.isISOControl(c) ? ' ' : c).forEach(clean::appendCodePoint);
    return clean.toString();
  }

  /** Appends text wrapped at word boundaries, each line starting with {@code indent}. */
  private static void appendWrapped(StringBuilder out, String text, String indent) {
    StringBuilder line = new StringBuilder();
    for (String word : clean(text).split(" ")) {
      if (line.length() > 0 && line.length() + 1 + word.length() > MESSAGE_WIDTH) {
        out.append(indent).append(line).append('\n');
        line.setLength(0);
      }
      if (line.length() > 0) {
        line.append(' ');
      }
      line.append(word);
    }
    out.append(indent).append(line).append('\n');
  }

  static String table(List<String> headers, List<List<String>> rows, List<Boolean> rightAlign) {
    int[] widths = new int[headers.size()];
    for (int i = 0; i < headers.size(); i++) {
      widths[i] = headers.get(i).length();
    }
    for (List<String> row : rows) {
      for (int i = 0; i < row.size(); i++) {
        widths[i] = Math.max(widths[i], row.get(i).length());
      }
    }
    StringBuilder border = new StringBuilder("+");
    for (int width : widths) {
      border.append("-".repeat(width + 2)).append('+');
    }
    border.append('\n');

    StringBuilder out = new StringBuilder();
    out.append(border);
    out.append(row(headers, widths, List.of()));
    out.append(border);
    for (List<String> row : rows) {
      out.append(row(row, widths, rightAlign));
    }
    out.append(border);
    return out.toString();
  }

  private static String row(List<String> cells, int[] widths, List<Boolean> rightAlign) {
    StringBuilder out = new StringBuilder("|");
    for (int i = 0; i < cells.size(); i++) {
      String cell = cells.get(i);
      String padding = " ".repeat(widths[i] - cell.length());
      boolean right = i < rightAlign.size() && rightAlign.get(i);
      out.append(' ').append(right ? padding + cell : cell + padding).append(" |");
    }
    return out.append('\n').toString();
  }
}
