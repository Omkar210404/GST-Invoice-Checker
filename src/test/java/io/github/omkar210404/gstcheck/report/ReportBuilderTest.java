package io.github.omkar210404.gstcheck.report;

import static io.github.omkar210404.gstcheck.testutil.Fixtures.validInterStateLine;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.validLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import io.github.omkar210404.gstcheck.report.ReportData.CheckCount;
import io.github.omkar210404.gstcheck.report.ReportData.InvoiceReport;
import io.github.omkar210404.gstcheck.rules.RuleEngine;
import io.github.omkar210404.gstcheck.testutil.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportBuilderTest {

  private static final ReportBuilder.RunInfo RUN =
      new ReportBuilder.RunInfo("TEST", Fixtures.AS_OF, "in.csv", "cfg.yml", "2026-27");

  private static ReportData build(InvoiceLine... lines) {
    List<InvoiceLine> list = List.of(lines);
    List<Issue> issues = RuleEngine.standard().run(list, Fixtures.context());
    return ReportBuilder.build(RUN, list, issues);
  }

  @Test
  void aCleanRunHasZeroCountsAndCarriesTheNotices() {
    ReportData data = build(validLine().build());
    assertEquals(1, data.summary().lines());
    assertEquals(1, data.summary().invoices());
    assertEquals(0, data.summary().errors());
    assertEquals(0, data.summary().warnings());
    assertEquals(0, data.summary().invoicesWithIssues());
    assertTrue(data.checkCounts().isEmpty());
    assertTrue(data.issues().isEmpty());
    assertEquals(Notices.STRUCTURAL_ONLY, data.structuralNotice());
    assertEquals(Notices.DISCLAIMER, data.disclaimer());
    assertEquals("2026-10-12", data.asOf());
    assertEquals("gst-invoice-checker", data.tool());
  }

  @Test
  void linesAreGroupedIntoInvoicesBySupplierAndNumberInFileOrder() {
    ReportData data =
        build(
            validLine().lineNo(2).invoiceNo("B/1").build(),
            validLine().lineNo(3).invoiceNo("A/1").build(),
            validLine().lineNo(4).invoiceNo("B/1").description("second").build(),
            validLine()
                .lineNo(5)
                .invoiceNo("B/1")
                .supplierGstin(Fixtures.SUPPLIER_04)
                .placeOfSupply("04")
                .build());
    assertEquals(3, data.summary().invoices());
    assertEquals(
        List.of("B/1", "A/1", "B/1"),
        data.invoices().stream().map(InvoiceReport::invoiceNo).toList());
    assertEquals(2, data.invoices().get(0).lines().size());
    assertEquals(Fixtures.SUPPLIER_04, data.invoices().get(2).supplierGstin());
  }

  @Test
  void supplierCaseDoesNotSplitAnInvoice() {
    ReportData data =
        build(
            validLine().lineNo(2).build(),
            validLine().lineNo(3).supplierGstin(Fixtures.SUPPLIER_27.toLowerCase()).build());
    assertEquals(1, data.summary().invoices());
  }

  @Test
  void invoiceTotalsAreExactDecimalSums() {
    ReportData data =
        build(
            validLine()
                .lineNo(2)
                .taxableValue("0.10")
                .rate("0")
                .cgst("")
                .sgst("")
                .lineTotal("0.10")
                .quantity("")
                .build(),
            validLine()
                .lineNo(3)
                .taxableValue("0.20")
                .rate("0")
                .cgst("")
                .sgst("")
                .lineTotal("0.20")
                .quantity("")
                .build());
    InvoiceReport invoice = data.invoices().get(0);
    // 0.10 + 0.20 is 0.30 exactly; summed as doubles it would print 0.30000000000000004.
    assertEquals("0.30", invoice.taxableTotal());
    assertEquals("0.30", invoice.grandTotal());
  }

  @Test
  void taxTotalAddsCgstSgstIgstAndCess() {
    ReportData data =
        build(
            validLine().lineNo(2).cess("5.50").lineTotal("1185.50").build(),
            validInterStateLine().lineNo(3).build());
    assertEquals("365.50", data.invoices().get(0).taxTotal());
  }

  @Test
  void unreadableAmountsAreLeftOutOfTotalsRatherThanGuessed() {
    ReportData data =
        build(
            validLine().lineNo(2).build(), validLine().lineNo(3).taxableValue("1,000.00").build());
    assertEquals("1000.00", data.invoices().get(0).taxableTotal());
    assertEquals("1,000.00", data.invoices().get(0).lines().get(1).taxableValue());
  }

  @Test
  void countsSeparateErrorsFromWarnings() {
    ReportData data =
        build(
            validLine().lineNo(2).invoiceNo("A/1").hsnOrSac("12").build(),
            validLine()
                .lineNo(3)
                .invoiceNo("A/2")
                .buyerGstin(Fixtures.BUYER_27.toLowerCase())
                .build(),
            validLine().lineNo(4).invoiceNo("A/3").build());
    assertEquals(1, data.summary().errors());
    assertEquals(1, data.summary().warnings());
    assertEquals(2, data.summary().linesWithIssues());
    assertEquals(1, data.summary().invoicesWithErrors());
    assertEquals(2, data.summary().invoicesWithIssues());
    assertEquals(1, data.invoices().get(0).errors());
    assertEquals(1, data.invoices().get(1).warnings());
    assertEquals(0, data.invoices().get(2).errors() + data.invoices().get(2).warnings());
  }

  @Test
  void checkCountsListOnlyChecksThatFiredInCatalogueOrder() {
    ReportData data =
        build(
            validLine()
                .lineNo(2)
                .invoiceNo("A/1")
                .rate("7")
                .cgst("35")
                .sgst("35")
                .lineTotal("1070")
                .build(),
            validLine().lineNo(3).invoiceNo("A/2").hsnOrSac("12").build(),
            validLine().lineNo(4).invoiceNo("A/3").hsnOrSac("123").build());
    assertEquals(
        List.of("HSN-002", "RATE-001"),
        data.checkCounts().stream().map(CheckCount::checkId).toList());
    assertEquals(2, data.checkCounts().get(0).count());
    assertEquals("error", data.checkCounts().get(0).severity());
  }
}
