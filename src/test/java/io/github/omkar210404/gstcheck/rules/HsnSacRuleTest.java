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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HsnSacRuleTest {

  private final HsnSacRule rule = new HsnSacRule();

  private List<Issue> check(String code) {
    return run(rule, validLine().hsnOrSac(code).build());
  }

  @ParameterizedTest
  @ValueSource(strings = {"1234", "123456", "12345678", "0101", "998877"})
  void allowedLengthsPass(String code) {
    assertTrue(check(code).isEmpty(), code);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "12", "123", "12345", "1234567", "123456789"})
  void otherLengthsFail(String code) {
    assertEquals(List.of("HSN-002"), ids(check(code)), code);
  }

  @ParameterizedTest
  @ValueSource(strings = {"84A1", "8471.30", "84 71", "8471-30", "abcd", "८४७१", "+1234"})
  void anythingButAsciiDigitsFails(String code) {
    assertEquals(List.of("HSN-001"), ids(check(code)), code);
  }

  @Test
  void theMessageListsTheAllowedLengthsInOrder() {
    assertTrue(check("12345").get(0).message().contains("4, 6, 8"));
  }

  @Test
  void aCodeOneDigitShortGetsTheLeadingZeroHint() {
    assertTrue(check("12345").get(0).suggestedFix().contains("leading zero"));
    assertTrue(check("123").get(0).suggestedFix().contains("leading zero"));
    assertFalse(check("123456789").get(0).suggestedFix().contains("leading zero"));
  }

  @Test
  void allowedLengthsComeFromTheConfig() {
    RuleContext context =
        Fixtures.context(Fixtures.configWith("allowed_lengths: [4, 6, 8]", "allowed_lengths: [8]"));
    assertEquals(
        List.of("HSN-002"), ids(run(rule, validLine().hsnOrSac("123456").build(), context)));
    assertTrue(run(rule, validLine().hsnOrSac("12345678").build(), context).isEmpty());
  }

  @Test
  void anEmptyCodeIsLeftToTheRequiredFieldCheck() {
    assertTrue(check("").isEmpty());
  }
}
