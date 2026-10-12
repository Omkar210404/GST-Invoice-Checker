package io.github.omkar210404.gstcheck.report;

import java.util.List;

/**
 * Everything a report shows, already reduced to text and counts. All four report formats are
 * rendered from this one structure, so they cannot disagree with each other.
 *
 * <p>Amounts are strings: the exact text from the CSV for cells, and plain decimal strings for
 * totals. Nothing passes through a floating-point number on the way to a report.
 *
 * @param tool the tool name
 * @param toolVersion the tool version
 * @param asOf the date treated as "today" for the run
 * @param inputFile the input path as given on the command line
 * @param configFile the config path as given on the command line
 * @param financialYear the financial year label from the config
 * @param structuralNotice the caveat that a passing GSTIN is only structurally valid
 * @param disclaimer the not-tax-advice disclaimer
 * @param summary headline counts
 * @param checkCounts how often each check fired, for checks that fired at least once
 * @param invoices one entry per invoice, in order of first appearance
 * @param issues every issue, sorted by row then check ID
 */
public record ReportData(
    String tool,
    String toolVersion,
    String asOf,
    String inputFile,
    String configFile,
    String financialYear,
    String structuralNotice,
    String disclaimer,
    Summary summary,
    List<CheckCount> checkCounts,
    List<InvoiceReport> invoices,
    List<IssueReport> issues) {

  public ReportData {
    checkCounts = List.copyOf(checkCounts);
    invoices = List.copyOf(invoices);
    issues = List.copyOf(issues);
  }

  /**
   * Headline counts.
   *
   * @param lines data rows read
   * @param invoices distinct invoices (supplier GSTIN + invoice number)
   * @param errors error-level issues
   * @param warnings warning-level issues
   * @param linesWithIssues rows with at least one issue
   * @param invoicesWithErrors invoices with at least one error
   * @param invoicesWithIssues invoices with at least one issue of any severity
   */
  public record Summary(
      int lines,
      int invoices,
      int errors,
      int warnings,
      int linesWithIssues,
      int invoicesWithErrors,
      int invoicesWithIssues) {}

  /**
   * How often one check fired.
   *
   * @param checkId public check ID
   * @param severity effective severity label
   * @param title short description of the check
   * @param count number of issues
   */
  public record CheckCount(String checkId, String severity, String title, int count) {}

  /**
   * One issue, flattened for output.
   *
   * @param lineNo spreadsheet row (the header is row 1)
   * @param invoiceNo invoice number on that row
   * @param supplierGstin supplier GSTIN on that row, normalised
   * @param checkId public check ID
   * @param severity severity label
   * @param field canonical column name
   * @param value offending value
   * @param message what is wrong
   * @param suggestedFix what to do about it
   */
  public record IssueReport(
      int lineNo,
      String invoiceNo,
      String supplierGstin,
      String checkId,
      String severity,
      String field,
      String value,
      String message,
      String suggestedFix) {}

  /**
   * One invoice with its lines.
   *
   * @param supplierGstin supplier GSTIN, normalised
   * @param supplierName supplier name when the file has that column
   * @param invoiceNo invoice number
   * @param invoiceDate date text from the first line
   * @param buyerGstin buyer GSTIN from the first line; empty for an unregistered buyer
   * @param buyerName buyer name when the file has that column
   * @param placeOfSupply place of supply code from the first line
   * @param taxableTotal sum of taxable values that could be read
   * @param taxTotal sum of CGST, SGST, IGST and cess that could be read
   * @param grandTotal sum of line totals that could be read
   * @param errors error count on this invoice
   * @param warnings warning count on this invoice
   * @param lines the invoice's rows
   */
  public record InvoiceReport(
      String supplierGstin,
      String supplierName,
      String invoiceNo,
      String invoiceDate,
      String buyerGstin,
      String buyerName,
      String placeOfSupply,
      String taxableTotal,
      String taxTotal,
      String grandTotal,
      int errors,
      int warnings,
      List<LineReport> lines) {
    public InvoiceReport {
      lines = List.copyOf(lines);
    }
  }

  /**
   * One CSV row, as text.
   *
   * @param lineNo spreadsheet row (the header is row 1)
   */
  public record LineReport(
      int lineNo,
      String description,
      String hsnOrSac,
      String quantity,
      String unit,
      String unitPrice,
      String taxableValue,
      String rate,
      String cgst,
      String sgst,
      String igst,
      String cess,
      String lineTotal) {}
}
