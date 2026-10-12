package io.github.omkar210404.gstcheck.rules;

import static io.github.omkar210404.gstcheck.testutil.Fixtures.ids;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.run;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.validInterStateLine;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.validLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.model.Issue;
import io.github.omkar210404.gstcheck.testutil.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaxAmountRuleTest {

  private final TaxAmountRule rule = new TaxAmountRule();

  /** An inter-state line of 1000.00 at 18% (expected tax 180.00) with the given IGST. */
  private List<Issue> withIgst(String igst) {
    return run(rule, validInterStateLine().igst(igst).build());
  }

  @Test
  void exactTaxPasses() {
    assertTrue(run(rule, validLine().build()).isEmpty());
    assertTrue(withIgst("180.00").isEmpty());
  }

  @Test
  void aDifferenceExactlyEqualToTheToleranceStillPasses() {
    // Default tolerance is 0.01, inclusive.
    assertTrue(withIgst("180.01").isEmpty());
    assertTrue(withIgst("179.99").isEmpty());
  }

  @Test
  void aDifferenceJustOverTheToleranceFails() {
    assertEquals(List.of("MATH-001"), ids(withIgst("180.02")));
    assertEquals(List.of("MATH-001"), ids(withIgst("179.98")));
    assertEquals(List.of("MATH-001"), ids(withIgst("180.011")));
  }

  @Test
  void theBoundaryIsExactEvenWhereBinaryFloatingPointWouldNotBe() {
    // 0.1 x 3% = 0.003 exactly. With tolerance 0.01, tax 0.013 is exactly on the boundary and
    // 0.0131 is over it. Doubles cannot represent either product exactly.
    assertTrue(
        run(rule, validInterStateLine().taxableValue("0.1").rate("3").igst("0.013").build())
            .isEmpty());
    assertEquals(
        List.of("MATH-001"),
        ids(run(rule, validInterStateLine().taxableValue("0.1").rate("3").igst("0.0131").build())));
  }

  @Test
  void halvesRoundedToThePaisaStayWithinTheDefaultTolerance() {
    // 33.35 at 5% is 1.6675; halves of 0.83375 round to 0.83 each, total 1.66.
    assertTrue(
        run(rule, validLine().taxableValue("33.35").rate("5").cgst("0.83").sgst("0.83").build())
            .isEmpty());
  }

  @Test
  void theSumOfCgstSgstAndIgstIsWhatIsCompared() {
    assertTrue(run(rule, validLine().cgst("100").sgst("80").build()).isEmpty());
    assertEquals(List.of("MATH-001"), ids(run(rule, validLine().cgst("90").sgst("80").build())));
  }

  @Test
  void aZeroRateExpectsZeroTax() {
    assertTrue(run(rule, validLine().rate("0").cgst("0").sgst("0").build()).isEmpty());
    assertEquals(List.of("MATH-001"), ids(run(rule, validLine().rate("0").build())));
  }

  @Test
  void fractionalRatesAreComputedExactly() {
    // 1000.00 at 0.25% is 2.50.
    assertTrue(run(rule, validInterStateLine().rate("0.25").igst("2.50").build()).isEmpty());
    assertEquals(
        List.of("MATH-001"),
        ids(run(rule, validInterStateLine().rate("0.25").igst("2.52").build())));
  }

  @Test
  void theMessageShowsTheWorking() {
    Issue issue = withIgst("170.00").get(0);
    assertTrue(issue.message().contains("170.00"));
    assertTrue(issue.message().contains("180.00"));
    assertTrue(issue.message().contains("difference 10"));
    assertTrue(issue.message().contains("tolerance 0.01"));
  }

  @Test
  void theToleranceComesFromTheConfig() {
    RuleContext loose =
        Fixtures.context(Fixtures.configWith("tax_amount: \"0.01\"", "tax_amount: \"1.00\""));
    assertTrue(run(rule, validInterStateLine().igst("181.00").build(), loose).isEmpty());
    assertEquals(
        List.of("MATH-001"), ids(run(rule, validInterStateLine().igst("181.01").build(), loose)));

    RuleContext exact =
        Fixtures.context(Fixtures.configWith("tax_amount: \"0.01\"", "tax_amount: \"0\""));
    assertEquals(
        List.of("MATH-001"), ids(run(rule, validInterStateLine().igst("180.01").build(), exact)));
    assertTrue(run(rule, validInterStateLine().igst("180.00").build(), exact).isEmpty());
  }

  @Test
  void theRuleStaysSilentWhenAnInputIsMissingInvalidOrNegative() {
    assertTrue(run(rule, validLine().taxableValue("").build()).isEmpty());
    assertTrue(run(rule, validLine().rate("abc").build()).isEmpty());
    assertTrue(run(rule, validLine().cgst("x").build()).isEmpty());
    assertTrue(run(rule, validLine().taxableValue("-1000.00").build()).isEmpty());
    assertTrue(run(rule, validLine().rate("-18").build()).isEmpty());
  }
}
