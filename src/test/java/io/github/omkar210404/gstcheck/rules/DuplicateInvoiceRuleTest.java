package io.github.omkar210404.gstcheck.rules;

import static io.github.omkar210404.gstcheck.testutil.Fixtures.ids;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.validLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import io.github.omkar210404.gstcheck.testutil.Fixtures;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DuplicateInvoiceRuleTest {

  private final DuplicateInvoiceRule rule = new DuplicateInvoiceRule();

  private List<Issue> run(InvoiceLine... lines) {
    return rule.apply(List.of(lines), Fixtures.context());
  }

  private static InvoiceLine.Builder on(int row, String date) {
    return validLine().lineNo(row).invoiceDateText(date).invoiceDate(LocalDate.parse(date));
  }

  @Test
  void severalLinesOfOneInvoiceAreNotDuplicates() {
    assertTrue(
        run(
                validLine().lineNo(2).build(),
                validLine().lineNo(3).description("second line").build(),
                validLine().lineNo(4).description("third line").build())
            .isEmpty());
  }

  @Test
  void sameNumberWithADifferentDateIsADuplicate() {
    List<Issue> issues = run(on(2, "2026-07-15").build(), on(3, "2026-07-16").build());
    assertEquals(List.of("INV-003"), ids(issues));
    Issue issue = issues.get(0);
    assertEquals(3, issue.lineNo(), "reported on the later row");
    assertTrue(issue.message().contains("row 2"));
    assertTrue(issue.message().contains("invoice date"));
    assertTrue(issue.message().contains("2026-27"));
  }

  @Test
  void sameNumberWithADifferentBuyerIsADuplicate() {
    List<Issue> issues =
        run(validLine().lineNo(2).build(), validLine().lineNo(3).buyerGstin("").build());
    assertEquals(List.of("INV-003"), ids(issues));
    assertTrue(issues.get(0).message().contains("buyer GSTIN"));
  }

  @Test
  void sameNumberWithADifferentPlaceOfSupplyIsADuplicate() {
    List<Issue> issues =
        run(validLine().lineNo(2).build(), validLine().lineNo(3).placeOfSupply("24").build());
    assertEquals(List.of("INV-003"), ids(issues));
    assertTrue(issues.get(0).message().contains("place of supply"));
  }

  @Test
  void everyConflictingRowIsReportedAgainstTheFirst() {
    List<Issue> issues =
        run(
            on(2, "2026-07-15").build(),
            on(3, "2026-07-16").build(),
            on(4, "2026-07-15").build(),
            on(5, "2026-07-17").build());
    assertEquals(List.of(3, 5), issues.stream().map(Issue::lineNo).toList());
  }

  @Test
  void theSameNumberFromAnotherSupplierIsFine() {
    assertTrue(
        run(
                on(2, "2026-07-15").build(),
                on(3, "2026-07-16").supplierGstin(Fixtures.SUPPLIER_04).build())
            .isEmpty());
  }

  @Test
  void theSameNumberInAnotherFinancialYearIsFine() {
    // 31 March 2027 is in 2026-27; 1 April 2027 starts 2027-28, where the number may be reused.
    assertTrue(run(on(2, "2027-03-31").build(), on(3, "2027-04-01").build()).isEmpty());
  }

  @Test
  void supplierIsComparedIgnoringCaseAndSpaces() {
    List<Issue> issues =
        run(
            on(2, "2026-07-15").build(),
            on(3, "2026-07-16")
                .supplierGstin(" " + Fixtures.SUPPLIER_27.toLowerCase() + " ")
                .build());
    assertEquals(List.of("INV-003"), ids(issues));
  }

  @Test
  void buyerCaseDifferencesAloneAreNotAConflict() {
    assertTrue(
        run(
                validLine().lineNo(2).build(),
                validLine().lineNo(3).buyerGstin(Fixtures.BUYER_27.toLowerCase()).build())
            .isEmpty());
  }

  @Test
  void numbersThatDifferOnlyByCaseGetAWarning() {
    List<Issue> issues =
        run(
            validLine().lineNo(2).invoiceNo("INV/26-27/001").build(),
            validLine().lineNo(3).invoiceNo("inv/26-27/001").build());
    assertEquals(List.of("INV-004"), ids(issues));
    Issue issue = issues.get(0);
    assertFalse(issue.isError());
    assertEquals(3, issue.lineNo());
    assertTrue(issue.message().contains("INV/26-27/001"));
    assertTrue(issue.message().contains("row 2"));
  }

  @Test
  void theCaseWarningIsRaisedOncePerSpellingNotPerLine() {
    List<Issue> issues =
        run(
            validLine().lineNo(2).invoiceNo("INV/1").build(),
            validLine().lineNo(3).invoiceNo("inv/1").build(),
            validLine().lineNo(4).invoiceNo("inv/1").description("second line").build());
    assertEquals(List.of("INV-004"), ids(issues));
  }

  @Test
  void rowsWithoutANumberSupplierOrDateAreSkipped() {
    assertTrue(
        run(
                validLine().lineNo(2).invoiceNo("").build(),
                validLine().lineNo(3).invoiceNo("").placeOfSupply("24").build(),
                validLine().lineNo(4).supplierGstin("").build(),
                validLine().lineNo(5).supplierGstin("").placeOfSupply("24").build(),
                validLine().lineNo(6).invoiceDate(null).build(),
                validLine().lineNo(7).invoiceDate(null).placeOfSupply("24").build())
            .isEmpty());
  }
}
