package io.github.omkar210404.gstcheck.rules;

import static io.github.omkar210404.gstcheck.testutil.Fixtures.ids;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.run;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.validLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.model.Issue;
import io.github.omkar210404.gstcheck.testutil.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * These tests set their own slab list. They say nothing about which GST rates are in force; they
 * only show that the rule follows whatever the config lists.
 */
class RateSlabRuleTest {

  private final RateSlabRule rule = new RateSlabRule();

  private static final RuleContext CONTEXT =
      Fixtures.context(
          Fixtures.configWith(
              "allowed_slabs: [0, 0.25, 3, 5, 12, 18, 28, 40]", "allowed_slabs: [0, 0.5, 7, 19]"));

  private List<Issue> check(String rate) {
    return run(rule, validLine().rate(rate).build(), CONTEXT);
  }

  @Test
  void ratesInTheConfiguredListPass() {
    for (String rate : List.of("0", "0.5", "7", "19")) {
      assertTrue(check(rate).isEmpty(), rate);
    }
  }

  @Test
  void ratesAreComparedByValueSoTrailingZerosDoNotMatter() {
    assertTrue(check("7.0").isEmpty());
    assertTrue(check("7.00").isEmpty());
    assertTrue(check("0.50").isEmpty());
    assertTrue(check("0.0").isEmpty());
  }

  @Test
  void ratesNotInTheListFail() {
    for (String rate : List.of("18", "5", "7.5", "0.05", "19.01", "100")) {
      assertEquals(List.of("RATE-001"), ids(check(rate)), rate);
    }
  }

  @Test
  void theMessageListsTheConfiguredSlabsAndPointsAtOfficialNotifications() {
    Issue issue = check("18").get(0);
    assertEquals("rate", issue.field());
    assertEquals("18", issue.value());
    assertTrue(issue.message().contains("0, 0.5, 7, 19"));
    assertTrue(issue.suggestedFix().contains("official GST notifications"));
  }

  @Test
  void theRuleStaysSilentForMissingInvalidOrNegativeRates() {
    assertTrue(check("").isEmpty());
    assertTrue(check("18%").isEmpty());
    assertTrue(check("-18").isEmpty());
  }
}
