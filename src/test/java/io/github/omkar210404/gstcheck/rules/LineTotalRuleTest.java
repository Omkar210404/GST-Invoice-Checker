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

class LineTotalRuleTest {

  private final LineTotalRule rule = new LineTotalRule();

  /** The valid line (1000.00 + 90.00 + 90.00, expected total 1180.00) with the given total. */
  private List<Issue> withTotal(String total) {
    return run(rule, validLine().lineTotal(total).build());
  }

  @Test
  void theExactTotalPasses() {
    assertTrue(withTotal("1180.00").isEmpty());
    assertTrue(run(rule, validInterStateLine().build()).isEmpty());
  }

  @Test
  void aDifferenceExactlyEqualToTheToleranceStillPasses() {
    assertTrue(withTotal("1180.01").isEmpty());
    assertTrue(withTotal("1179.99").isEmpty());
  }

  @Test
  void aDifferenceJustOverTheToleranceFails() {
    assertEquals(List.of("MATH-002"), ids(withTotal("1180.02")));
    assertEquals(List.of("MATH-002"), ids(withTotal("1179.98")));
  }

  @Test
  void cessIsPartOfTheTotal() {
    assertTrue(run(rule, validLine().cess("12.50").lineTotal("1192.50").build()).isEmpty());
    assertEquals(List.of("MATH-002"), ids(run(rule, validLine().cess("12.50").build())));
  }

  @Test
  void blankTaxAndCessCellsCountAsZero() {
    assertTrue(
        run(rule, validLine().cgst("").sgst("").igst("").cess("").lineTotal("1000.00").build())
            .isEmpty());
  }

  @Test
  void theMessageShowsTheExpectedTotal() {
    Issue issue = withTotal("1190.00").get(0);
    assertEquals("line_total", issue.field());
    assertTrue(issue.message().contains("1190.00"));
    assertTrue(issue.message().contains("1180.00"));
    assertTrue(issue.message().contains("difference 10"));
  }

  @Test
  void theToleranceComesFromTheConfig() {
    RuleContext loose =
        Fixtures.context(Fixtures.configWith("line_total: \"0.01\"", "line_total: \"0.50\""));
    assertTrue(run(rule, validLine().lineTotal("1180.50").build(), loose).isEmpty());
    assertEquals(
        List.of("MATH-002"), ids(run(rule, validLine().lineTotal("1180.51").build(), loose)));
  }

  @Test
  void theRuleStaysSilentWhenAnInputIsMissingOrInvalid() {
    assertTrue(withTotal("").isEmpty());
    assertTrue(withTotal("abc").isEmpty());
    assertTrue(run(rule, validLine().taxableValue("").build()).isEmpty());
    assertTrue(run(rule, validLine().cess("n/a").build()).isEmpty());
    assertTrue(run(rule, validLine().igst("?").build()).isEmpty());
  }
}
