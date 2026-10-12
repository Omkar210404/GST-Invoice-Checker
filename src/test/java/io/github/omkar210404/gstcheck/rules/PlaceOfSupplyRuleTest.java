package io.github.omkar210404.gstcheck.rules;

import static io.github.omkar210404.gstcheck.testutil.Fixtures.ids;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.run;
import static io.github.omkar210404.gstcheck.testutil.Fixtures.validLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.model.Issue;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlaceOfSupplyRuleTest {

  private final PlaceOfSupplyRule rule = new PlaceOfSupplyRule();

  private List<Issue> check(String code) {
    return run(rule, validLine().placeOfSupply(code).build());
  }

  @ParameterizedTest
  @ValueSource(strings = {"27", "07", "04", "38"})
  void configuredCodesPass(String code) {
    assertTrue(check(code).isEmpty(), code);
  }

  @ParameterizedTest
  @ValueSource(strings = {"99", "00", "MH", "027", "27.0", "Maharashtra"})
  void anythingNotInTheConfigFails(String code) {
    assertEquals(List.of("POS-001"), ids(check(code)), code);
  }

  @Test
  void aSingleDigitGetsTheLeadingZeroHint() {
    Issue issue = check("7").get(0);
    assertEquals("POS-001", issue.check().id());
    assertTrue(issue.suggestedFix().contains("07"));
    assertTrue(issue.suggestedFix().contains("leading zero"));
  }

  @Test
  void anUnknownCodeGetsTheGeneralHint() {
    assertFalse(check("99").get(0).suggestedFix().contains("leading zero"));
    assertTrue(check("99").get(0).suggestedFix().contains("states"));
  }

  @Test
  void anEmptyCodeIsLeftToTheRequiredFieldCheck() {
    assertTrue(check("").isEmpty());
  }
}
