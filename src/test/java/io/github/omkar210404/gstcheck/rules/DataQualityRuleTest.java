package io.github.omkar210404.gstcheck.rules;

import static io.github.omkar210404.gstcheck.testutil.Fixtures.ids;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.run;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.validLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataQualityRuleTest {

  private final DataQualityRule rule = new DataQualityRule();

  @Test
  void aCompleteLineHasNoIssues() {
    assertTrue(run(rule, validLine().build()).isEmpty());
  }

  @Test
  void optionalCellsMayBeEmpty() {
    InvoiceLine line =
        validLine()
            .buyerGstin("")
            .description("")
            .quantity("")
            .unit("")
            .unitPrice("")
            .cgst("")
            .sgst("")
            .igst("")
            .cess("")
            .build();
    assertTrue(run(rule, line).isEmpty());
  }

  @Test
  void eachRequiredCellIsReportedWhenEmpty() {
    InvoiceLine line =
        validLine()
            .invoiceNo("")
            .invoiceDateText("")
            .invoiceDate(null)
            .supplierGstin("   ")
            .placeOfSupply("")
            .hsnOrSac("")
            .taxableValue("")
            .rate("")
            .lineTotal("")
            .build();
    List<Issue> issues = run(rule, line);
    assertEquals(8, issues.size());
    assertTrue(issues.stream().allMatch(issue -> issue.check().id().equals("DATA-001")));
    assertEquals(
        List.of(
            "invoice_no",
            "invoice_date",
            "supplier_gstin",
            "place_of_supply_state_code",
            "hsn_or_sac",
            "taxable_value",
            "rate",
            "line_total"),
        issues.stream().map(Issue::field).toList());
  }

  @Test
  void numbersThatAreNotPlainDecimalsAreReportedWithTheirText() {
    InvoiceLine line = validLine().taxableValue("1,000.00").rate("18%").cess("n/a").build();
    List<Issue> issues = run(rule, line);
    assertEquals(List.of("DATA-002", "DATA-002", "DATA-002"), ids(issues));
    assertEquals(
        List.of("taxable_value", "rate", "cess"), issues.stream().map(Issue::field).toList());
    assertEquals("1,000.00", issues.get(0).value());
  }

  @Test
  void anEmptyRequiredNumberIsMissingNotInvalid() {
    assertEquals(List.of("DATA-001"), ids(run(rule, validLine().rate(" ").build())));
  }

  @Test
  void issuesCarryTheRowAndInvoice() {
    Issue issue = run(rule, validLine().lineNo(17).hsnOrSac("").build()).get(0);
    assertEquals(17, issue.lineNo());
    assertEquals("INV/26-27/001", issue.invoiceNo());
    assertTrue(issue.isError());
  }
}
