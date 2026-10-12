package io.github.omkar210404.gstcheck.rules;

import static io.github.omkar210404.gstcheck.testutil.Fixtures.ids;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.validInterStateLine;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.validLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import io.github.omkar210404.gstcheck.model.Severity;
import io.github.omkar210404.gstcheck.testutil.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleEngineTest {

  private final RuleEngine engine = RuleEngine.standard();

  @Test
  void validLinesProduceNoIssues() {
    List<InvoiceLine> lines =
        List.of(
            validLine().lineNo(2).build(),
            validInterStateLine().lineNo(3).invoiceNo("INV/26-27/002").build());
    assertTrue(engine.run(lines, Fixtures.context()).isEmpty());
  }

  @Test
  void noLinesProduceNoIssues() {
    assertTrue(engine.run(List.of(), Fixtures.context()).isEmpty());
  }

  @Test
  void issuesAreSortedByRowThenCheckId() {
    List<InvoiceLine> lines =
        List.of(
            validLine().lineNo(9).rate("7").cgst("35").sgst("35").lineTotal("1070").build(),
            validLine().lineNo(4).invoiceNo("BAD NUMBER").hsnOrSac("12").build());
    List<Issue> issues = engine.run(lines, Fixtures.context());
    assertEquals(List.of(4, 4, 9), issues.stream().map(Issue::lineNo).toList());
    assertEquals(List.of("HSN-002", "INV-002", "RATE-001"), ids(issues));
  }

  @Test
  void oneBadCellDoesNotCascadeIntoUnrelatedChecks() {
    // An unparseable taxable value is reported once; the maths rules stay silent.
    List<Issue> issues =
        engine.run(List.of(validLine().taxableValue("1,000.00").build()), Fixtures.context());
    assertEquals(List.of("DATA-002"), ids(issues));
  }

  @Test
  void disabledChecksAreDropped() {
    List<InvoiceLine> lines =
        List.of(validLine().taxableValue("900.00").cgst("81").sgst("81").lineTotal("1062").build());
    assertEquals(List.of("MATH-003"), ids(engine.run(lines, Fixtures.context())));

    RuleContext context =
        Fixtures.context(Fixtures.configWith("disabled: []", "disabled: [\"MATH-003\"]"));
    assertTrue(engine.run(lines, context).isEmpty());
  }

  @Test
  void severityOverridesAreApplied() {
    List<InvoiceLine> lines =
        List.of(validLine().buyerGstin(Fixtures.BUYER_27.toLowerCase()).build());
    Issue byDefault = engine.run(lines, Fixtures.context()).get(0);
    assertEquals(Severity.WARNING, byDefault.severity());

    RuleContext context =
        Fixtures.context(
            Fixtures.configWith("severity: {}", "severity: { \"GSTIN-007\": \"error\" }"));
    Issue overridden = engine.run(lines, context).get(0);
    assertEquals("GSTIN-007", overridden.check().id());
    assertEquals(Severity.ERROR, overridden.severity());
    assertTrue(overridden.isError());
  }

  @Test
  void anErrorCanBeDowngradedToAWarning() {
    RuleContext context =
        Fixtures.context(
            Fixtures.configWith("severity: {}", "severity: { \"GSTIN-005\": \"warning\" }"));
    Issue issue =
        engine
            .run(List.of(validLine().buyerGstin(Fixtures.gstin("27PQRXS5678T1Y")).build()), context)
            .get(0);
    assertEquals("GSTIN-005", issue.check().id());
    assertFalse(issue.isError());
  }

  @Test
  void theResultCannotBeModified() {
    List<Issue> issues =
        engine.run(List.of(validLine().hsnOrSac("12").build()), Fixtures.context());
    assertThrows(UnsupportedOperationException.class, issues::clear);
  }

  @Test
  void aCustomRuleSetCanBeSupplied() {
    RuleEngine onlyRates = new RuleEngine(List.of(new RateSlabRule()));
    List<Issue> issues =
        onlyRates.run(List.of(validLine().rate("7").hsnOrSac("12").build()), Fixtures.context());
    assertEquals(List.of("RATE-001"), ids(issues));
  }
}
