package io.github.omkar210404.gstcheck.rules;

import static io.github.omkar210404.gstcheck.testutil.Fixtures.ids;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.run;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.validLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.model.Issue;
import io.github.omkar210404.gstcheck.testutil.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class GstinRuleTest {

  private final GstinRule rule = new GstinRule();

  private static String withWrongCheckCharacter(String gstin) {
    return gstin.substring(0, 14) + (gstin.charAt(14) == 'A' ? 'B' : 'A');
  }

  @Test
  void validSupplierAndBuyerPass() {
    assertTrue(run(rule, validLine().build()).isEmpty());
  }

  @Test
  void anEmptyBuyerGstinMeansAnUnregisteredBuyerAndIsFine() {
    assertTrue(run(rule, validLine().buyerGstin("").build()).isEmpty());
    assertTrue(run(rule, validLine().buyerGstin("   ").build()).isEmpty());
  }

  @Test
  void anEmptySupplierGstinIsLeftToTheRequiredFieldCheck() {
    assertTrue(run(rule, validLine().supplierGstin("").build()).isEmpty());
  }

  @Test
  void aBadSupplierCheckCharacterIsReportedOnTheSupplierColumn() {
    String bad = withWrongCheckCharacter(Fixtures.SUPPLIER_27);
    Issue issue = run(rule, validLine().supplierGstin(bad).build()).get(0);
    assertEquals("GSTIN-006", issue.check().id());
    assertEquals("supplier_gstin", issue.field());
    assertEquals(bad, issue.value());
    assertFalse(issue.suggestedFix().isBlank());
  }

  @Test
  void aBadBuyerGstinIsReportedOnTheBuyerColumn() {
    Issue issue =
        run(rule, validLine().buyerGstin(withWrongCheckCharacter(Fixtures.BUYER_27)).build())
            .get(0);
    assertEquals("GSTIN-006", issue.check().id());
    assertEquals("buyer_gstin", issue.field());
  }

  @Test
  void bothColumnsAreCheckedIndependently() {
    List<Issue> issues =
        run(
            rule,
            validLine()
                .supplierGstin(Fixtures.SUPPLIER_27.substring(0, 14))
                .buyerGstin(Fixtures.gstin("99PQRXS5678T1Z"))
                .build());
    assertEquals(List.of("GSTIN-001", "GSTIN-002"), ids(issues));
    assertEquals("supplier_gstin", issues.get(0).field());
    assertEquals("buyer_gstin", issues.get(1).field());
  }

  @Test
  void aStateCodeMissingFromTheConfigIsRejected() {
    // The example config has no state 99.
    assertEquals(
        List.of("GSTIN-002"),
        ids(run(rule, validLine().buyerGstin(Fixtures.gstin("99PQRXS5678T1Z")).build())));
  }

  @Test
  void lowerCaseIsOnlyAWarning() {
    Issue issue = run(rule, validLine().buyerGstin(Fixtures.BUYER_27.toLowerCase()).build()).get(0);
    assertEquals("GSTIN-007", issue.check().id());
    assertFalse(issue.isError());
  }

  @Test
  void theIssueRecordsTheNormalisedSupplierForGrouping() {
    Issue issue =
        run(
                rule,
                validLine()
                    .supplierGstin(" " + Fixtures.SUPPLIER_27.toLowerCase())
                    .buyerGstin("")
                    .build())
            .get(0);
    assertEquals(Fixtures.SUPPLIER_27, issue.supplierGstin());
  }
}
