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

class QuantityPriceRuleTest {

  private final QuantityPriceRule rule = new QuantityPriceRule();

  @Test
  void quantityTimesPricePasses() {
    assertTrue(run(rule, validLine().build()).isEmpty());
  }

  @Test
  void fractionalQuantitiesAreMultipliedExactly() {
    // 2.5 x 399.99 = 999.975
    assertTrue(
        run(rule, validLine().quantity("2.5").unitPrice("399.99").taxableValue("999.98").build())
            .isEmpty());
    assertTrue(
        run(rule, validLine().quantity("2.5").unitPrice("399.99").taxableValue("999.97").build())
            .isEmpty());
    assertEquals(
        List.of("MATH-003"),
        ids(
            run(
                rule,
                validLine().quantity("2.5").unitPrice("399.99").taxableValue("999.96").build())));
  }

  @Test
  void aDifferenceExactlyEqualToTheTolerancePassesAndJustOverFails() {
    assertTrue(run(rule, validLine().taxableValue("1000.01").build()).isEmpty());
    assertEquals(List.of("MATH-003"), ids(run(rule, validLine().taxableValue("1000.02").build())));
  }

  @Test
  void aMismatchIsOnlyAWarningBecauseDiscountsAreNotModelled() {
    Issue issue = run(rule, validLine().taxableValue("900.00").build()).get(0);
    assertEquals("MATH-003", issue.check().id());
    assertFalse(issue.isError());
    assertTrue(issue.message().contains("1000.00"), issue.message());
    assertTrue(issue.suggestedFix().contains("discount"));
  }

  @Test
  void theRuleOnlyRunsWhenBothQuantityAndPriceArePresent() {
    assertTrue(run(rule, validLine().quantity("").taxableValue("900.00").build()).isEmpty());
    assertTrue(run(rule, validLine().unitPrice("").taxableValue("900.00").build()).isEmpty());
    assertTrue(run(rule, validLine().quantity("two").taxableValue("900.00").build()).isEmpty());
    assertTrue(run(rule, validLine().taxableValue("").build()).isEmpty());
  }

  @Test
  void theToleranceComesFromTheConfig() {
    RuleContext loose =
        Fixtures.context(
            Fixtures.configWith("quantity_price: \"0.01\"", "quantity_price: \"100\""));
    assertTrue(run(rule, validLine().taxableValue("900.00").build(), loose).isEmpty());
    assertEquals(
        List.of("MATH-003"), ids(run(rule, validLine().taxableValue("899.99").build(), loose)));
  }
}
