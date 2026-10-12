package io.github.omkar210404.gstcheck.rules;

import static io.github.omkar210404.gstcheck.testutil.Fixtures.ids;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.run;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.validInterStateLine;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.validLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.model.Issue;
import io.github.omkar210404.gstcheck.testutil.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaxTypeRuleTest {

  private final TaxTypeRule rule = new TaxTypeRule();

  @Test
  void intraStateWithEqualCgstAndSgstPasses() {
    assertTrue(run(rule, validLine().build()).isEmpty());
  }

  @Test
  void interStateWithIgstPasses() {
    assertTrue(run(rule, validInterStateLine().build()).isEmpty());
  }

  @Test
  void blankTaxCellsCountAsZero() {
    assertTrue(run(rule, validLine().igst("").build()).isEmpty());
    assertTrue(run(rule, validInterStateLine().cgst("").sgst("").build()).isEmpty());
  }

  @Test
  void igstOnAnIntraStateSupplyIsFlagged() {
    List<Issue> issues = run(rule, validLine().cgst("0").sgst("0").igst("180.00").build());
    assertEquals(List.of("TAX-001"), ids(issues));
    assertTrue(issues.get(0).message().contains("27 Maharashtra"));
    assertTrue(issues.get(0).suggestedFix().contains("CGST + SGST"));
  }

  @Test
  void cgstAndSgstOnAnInterStateSupplyAreFlagged() {
    List<Issue> issues =
        run(rule, validInterStateLine().cgst("90.00").sgst("90.00").igst("0").build());
    assertEquals(List.of("TAX-002"), ids(issues));
    assertTrue(issues.get(0).message().contains("24 Gujarat"));
  }

  @Test
  void igstTogetherWithCgstOrSgstIsMixed() {
    assertEquals(
        List.of("TAX-003"), ids(run(rule, validLine().cgst("45").sgst("45").igst("90").build())));
    assertEquals(
        List.of("TAX-003"),
        ids(run(rule, validInterStateLine().cgst("0").sgst("10").igst("170").build())));
  }

  @Test
  void mixedTaxIsReportedOnceNotAlsoAsWrongType() {
    // Intra-state with IGST present would also match TAX-001; only TAX-003 is raised.
    assertEquals(1, run(rule, validLine().igst("1.00").build()).size());
  }

  @Test
  void unequalHalvesAreFlagged() {
    List<Issue> issues = run(rule, validLine().cgst("100.00").sgst("80.00").build());
    assertEquals(List.of("TAX-004"), ids(issues));
    assertTrue(issues.get(0).message().contains("100.00"));
    assertTrue(issues.get(0).message().contains("80.00"));
  }

  @Test
  void aMissingHalfIsFlaggedAsUnequal() {
    assertEquals(List.of("TAX-004"), ids(run(rule, validLine().sgst("").build())));
    assertEquals(List.of("TAX-004"), ids(run(rule, validLine().cgst("0").build())));
  }

  @Test
  void halvesAreComparedByValueNotByText() {
    assertTrue(run(rule, validLine().cgst("90").sgst("90.00").build()).isEmpty());
  }

  @Test
  void aOnePaisaDifferenceBetweenHalvesIsStillFlagged() {
    assertEquals(
        List.of("TAX-004"), ids(run(rule, validLine().cgst("90.01").sgst("90.00").build())));
  }

  @Test
  void aLineWithNoTaxAtAllIsNotClassified() {
    assertTrue(run(rule, validLine().cgst("0").sgst("0").igst("0").build()).isEmpty());
    assertTrue(run(rule, validInterStateLine().igst("").build()).isEmpty());
  }

  @Test
  void anIntraTerritorySupplyInAUtgstTerritoryUsesUtgstWording() {
    // Supplier and place of supply are both 04, which the example config marks utgst: true.
    List<Issue> issues =
        run(
            rule,
            validLine()
                .supplierGstin(Fixtures.SUPPLIER_04)
                .placeOfSupply("04")
                .cgst("0")
                .sgst("0")
                .igst("180.00")
                .build());
    assertEquals(List.of("TAX-001"), ids(issues));
    assertTrue(issues.get(0).suggestedFix().contains("CGST + UTGST"));
    assertTrue(issues.get(0).suggestedFix().contains("sgst column"));
  }

  @Test
  void aCorrectIntraTerritorySupplyInAUtgstTerritoryPasses() {
    assertTrue(
        run(rule, validLine().supplierGstin(Fixtures.SUPPLIER_04).placeOfSupply("04").build())
            .isEmpty());
  }

  @Test
  void equivalentStateCodesFromTheConfigMakeASupplyIntraState() {
    RuleContext context =
        Fixtures.context(
            Fixtures.configWith(
                "equivalent_state_codes: []", "equivalent_state_codes: [[\"27\", \"24\"]]"));
    // Supplier in 27, place of supply 24: inter-state by default, intra-state once grouped.
    assertTrue(
        run(rule, validLine().placeOfSupply("24").build(), context).isEmpty(),
        "CGST + SGST is right once the codes are grouped");
    assertEquals(List.of("TAX-001"), ids(run(rule, validInterStateLine().build(), context)));
    assertEquals(List.of("TAX-002"), ids(run(rule, validLine().placeOfSupply("24").build())));
  }

  @Test
  void theRuleStaysSilentWhenItCannotTellTheStates() {
    // Unknown place of supply: POS-001 reports it.
    assertTrue(run(rule, validLine().placeOfSupply("99").igst("180").build()).isEmpty());
    // Supplier state not configured: GSTIN-002 reports it.
    assertTrue(
        run(rule, validLine().supplierGstin(Fixtures.gstin("99ABCXD1234E1Z")).igst("180").build())
            .isEmpty());
    // Supplier missing.
    assertTrue(run(rule, validLine().supplierGstin("").igst("180").build()).isEmpty());
  }

  @Test
  void theRuleStaysSilentWhenATaxAmountIsNotANumber() {
    assertTrue(run(rule, validLine().igst("abc").build()).isEmpty());
  }

  @Test
  void supplierStateIsReadFromALowerCaseGstinToo() {
    List<Issue> issues =
        run(
            rule,
            validLine()
                .supplierGstin(Fixtures.SUPPLIER_27.toLowerCase())
                .cgst("0")
                .sgst("0")
                .igst("180")
                .build());
    assertEquals(List.of("TAX-001"), ids(issues));
    assertFalse(issues.get(0).message().isBlank());
  }
}
