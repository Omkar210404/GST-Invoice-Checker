package io.github.omkar210404.gstcheck.report;

import io.github.omkar210404.gstcheck.report.ReportData.IssueReport;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * The issues as a CSV, one row per issue, for filtering in a spreadsheet or feeding to a script.
 *
 * <p>Values copied from the input are untrusted. A cell starting with {@code =} or {@code @} (or
 * {@code +} or {@code -} when it is not a number) would be run as a formula by a spreadsheet, so
 * such cells are prefixed with an apostrophe.
 */
public final class IssuesCsvReport {

  static final String[] HEADER = {
    "line_no",
    "invoice_no",
    "supplier_gstin",
    "check_id",
    "severity",
    "field",
    "value",
    "message",
    "suggested_fix"
  };

  private static final CSVFormat FORMAT =
      CSVFormat.DEFAULT.builder().setHeader(HEADER).setRecordSeparator("\n").get();

  private IssuesCsvReport() {}

  public static String render(ReportData data) {
    StringBuilder out = new StringBuilder();
    try (CSVPrinter printer = new CSVPrinter(out, FORMAT)) {
      for (IssueReport issue : data.issues()) {
        printer.printRecord(
            issue.lineNo(),
            safe(issue.invoiceNo()),
            safe(issue.supplierGstin()),
            issue.checkId(),
            issue.severity(),
            issue.field(),
            safe(issue.value()),
            safe(issue.message()),
            safe(issue.suggestedFix()));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toString();
  }

  /** Neutralises spreadsheet formula injection without touching ordinary values or numbers. */
  static String safe(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    char first = value.charAt(0);
    boolean formulaStart = first == '=' || first == '@' || first == '\t' || first == '\r';
    boolean signedNonNumber =
        (first == '+' || first == '-') && !value.matches("[+-]\\d+(\\.\\d+)?");
    return formulaStart || signedNonNumber ? "'" + value : value;
  }
}
