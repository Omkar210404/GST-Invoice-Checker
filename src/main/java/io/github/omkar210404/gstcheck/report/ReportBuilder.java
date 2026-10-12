package io.github.omkar210404.gstcheck.report;

import io.github.omkar210404.gstcheck.model.Amount;
import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import io.github.omkar210404.gstcheck.model.Severity;
import io.github.omkar210404.gstcheck.report.ReportData.CheckCount;
import io.github.omkar210404.gstcheck.report.ReportData.InvoiceReport;
import io.github.omkar210404.gstcheck.report.ReportData.IssueReport;
import io.github.omkar210404.gstcheck.report.ReportData.LineReport;
import io.github.omkar210404.gstcheck.report.ReportData.Summary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns lines and issues into the {@link ReportData} that every report format renders. */
public final class ReportBuilder {

  private ReportBuilder() {}

  /**
   * Facts about the run that are not derived from the data.
   *
   * @param toolVersion the tool version
   * @param asOf the date treated as "today"
   * @param inputFile the input path as given
   * @param configFile the config path as given
   * @param financialYear the financial year label
   */
  public record RunInfo(
      String toolVersion,
      LocalDate asOf,
      String inputFile,
      String configFile,
      String financialYear) {}

  /** Supplier GSTIN plus invoice number: what the reports treat as one invoice. */
  private record InvoiceKey(String supplierGstin, String invoiceNo) {}

  public static ReportData build(RunInfo run, List<InvoiceLine> lines, List<Issue> issues) {
    Map<Integer, List<Issue>> issuesByLine = new HashMap<>();
    Map<CheckId, Integer> countByCheck = new EnumMap<>(CheckId.class);
    Map<CheckId, Severity> severityByCheck = new EnumMap<>(CheckId.class);
    int errors = 0;
    int warnings = 0;
    for (Issue issue : issues) {
      issuesByLine.computeIfAbsent(issue.lineNo(), n -> new ArrayList<>()).add(issue);
      countByCheck.merge(issue.check(), 1, Integer::sum);
      severityByCheck.put(issue.check(), issue.severity());
      if (issue.isError()) {
        errors++;
      } else {
        warnings++;
      }
    }

    Map<InvoiceKey, List<InvoiceLine>> grouped = new LinkedHashMap<>();
    for (InvoiceLine line : lines) {
      grouped
          .computeIfAbsent(
              new InvoiceKey(line.supplierGstinNormalized(), line.invoiceNo()),
              k -> new ArrayList<>())
          .add(line);
    }

    List<InvoiceReport> invoices = new ArrayList<>();
    int invoicesWithErrors = 0;
    int invoicesWithIssues = 0;
    for (Map.Entry<InvoiceKey, List<InvoiceLine>> entry : grouped.entrySet()) {
      InvoiceReport invoice = invoice(entry.getKey(), entry.getValue(), issuesByLine);
      invoices.add(invoice);
      if (invoice.errors() > 0) {
        invoicesWithErrors++;
      }
      if (invoice.errors() + invoice.warnings() > 0) {
        invoicesWithIssues++;
      }
    }

    List<CheckCount> checkCounts = new ArrayList<>();
    for (Map.Entry<CheckId, Integer> entry : countByCheck.entrySet()) {
      CheckId check = entry.getKey();
      checkCounts.add(
          new CheckCount(
              check.id(), severityByCheck.get(check).label(), check.title(), entry.getValue()));
    }

    Set<Integer> linesWithIssues = new HashSet<>(issuesByLine.keySet());
    Summary summary =
        new Summary(
            lines.size(),
            invoices.size(),
            errors,
            warnings,
            linesWithIssues.size(),
            invoicesWithErrors,
            invoicesWithIssues);

    List<IssueReport> issueReports = new ArrayList<>();
    for (Issue issue : issues) {
      issueReports.add(
          new IssueReport(
              issue.lineNo(),
              issue.invoiceNo(),
              issue.supplierGstin(),
              issue.check().id(),
              issue.severity().label(),
              issue.field(),
              issue.value(),
              issue.message(),
              issue.suggestedFix()));
    }

    return new ReportData(
        Notices.TOOL_NAME,
        run.toolVersion(),
        run.asOf().toString(),
        run.inputFile(),
        run.configFile(),
        run.financialYear(),
        Notices.STRUCTURAL_ONLY,
        Notices.DISCLAIMER,
        summary,
        checkCounts,
        invoices,
        issueReports);
  }

  private static InvoiceReport invoice(
      InvoiceKey key, List<InvoiceLine> lines, Map<Integer, List<Issue>> issuesByLine) {
    InvoiceLine first = lines.get(0);
    BigDecimal taxable = BigDecimal.ZERO;
    BigDecimal tax = BigDecimal.ZERO;
    BigDecimal total = BigDecimal.ZERO;
    int errors = 0;
    int warnings = 0;
    List<LineReport> lineReports = new ArrayList<>();
    for (InvoiceLine line : lines) {
      taxable = taxable.add(line.taxableValue().orZero());
      tax =
          tax.add(line.cgst().orZero())
              .add(line.sgst().orZero())
              .add(line.igst().orZero())
              .add(line.cess().orZero());
      total = total.add(line.lineTotal().orZero());
      for (Issue issue : issuesByLine.getOrDefault(line.lineNo(), List.of())) {
        if (issue.isError()) {
          errors++;
        } else {
          warnings++;
        }
      }
      lineReports.add(
          new LineReport(
              line.lineNo(),
              line.description(),
              line.hsnOrSac(),
              line.quantity().text(),
              line.unit(),
              line.unitPrice().text(),
              line.taxableValue().text(),
              line.rate().text(),
              text(line.cgst()),
              text(line.sgst()),
              text(line.igst()),
              text(line.cess()),
              line.lineTotal().text()));
    }
    return new InvoiceReport(
        key.supplierGstin(),
        first.supplierName(),
        key.invoiceNo(),
        first.invoiceDateText(),
        first.buyerGstinNormalized(),
        first.buyerName(),
        first.placeOfSupply(),
        taxable.toPlainString(),
        tax.toPlainString(),
        total.toPlainString(),
        errors,
        warnings,
        lineReports);
  }

  private static String text(Amount amount) {
    return amount.text();
  }
}
