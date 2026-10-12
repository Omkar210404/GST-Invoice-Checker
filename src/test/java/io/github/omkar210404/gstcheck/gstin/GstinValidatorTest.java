package io.github.omkar210404.gstcheck.gstin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.testutil.Fixtures;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GstinValidatorTest {

  private static final String VALID = Fixtures.SUPPLIER_27;

  private final GstinValidator validator = new GstinValidator(Set.of("27", "24", "04"));

  private List<CheckId> checks(String gstin) {
    return validator.validate(gstin).problems().stream().map(GstinProblem::check).toList();
  }

  @Test
  void acceptsAStructurallyValidGstin() {
    GstinResult result = validator.validate(VALID);
    assertTrue(result.structurallyValid());
    assertTrue(result.problems().isEmpty());
    assertEquals(VALID, result.normalized());
  }

  @Test
  void theHandWorkedDummyGstinIsValid() {
    assertTrue(validator.validate("27AAAAA0000A1Z2").structurallyValid());
  }

  @Test
  void nullAndEmptyAreReportedNotThrown() {
    assertEquals(List.of(CheckId.GSTIN_001), checks(null));
    assertEquals(List.of(CheckId.GSTIN_001), checks(""));
    assertFalse(validator.validate(null).structurallyValid());
  }

  @Test
  void fourteenAndSixteenCharactersFailTheLengthCheck() {
    assertEquals(List.of(CheckId.GSTIN_001), checks(VALID.substring(0, 14)));
    assertEquals(List.of(CheckId.GSTIN_001), checks(VALID + "0"));
  }

  @Test
  void charactersOutsideLettersAndDigitsFailTheShapeCheck() {
    assertEquals(List.of(CheckId.GSTIN_001), checks("27ABCXD1234E1Z-"));
    assertEquals(List.of(CheckId.GSTIN_001), checks("27ABCXD 234E1Z5"));
  }

  @Test
  void whenTheShapeIsWrongNoPositionalChecksAreReported() {
    // A short GSTIN has no meaningful "character 14", so only GSTIN-001 is raised.
    assertEquals(List.of(CheckId.GSTIN_001), checks("99"));
  }

  @Test
  void unknownStateCodeIsRejected() {
    assertEquals(List.of(CheckId.GSTIN_002), checks(Fixtures.gstin("99ABCXD1234E1Z")));
  }

  @Test
  void stateCodeMustBeDigits() {
    assertEquals(List.of(CheckId.GSTIN_002), checks(Fixtures.gstin("A7ABCXD1234E1Z")));
  }

  @Test
  void stateCodeListComesFromTheCaller() {
    String delhi = Fixtures.gstin("07ABCXD1234E1Z");
    assertEquals(List.of(CheckId.GSTIN_002), checks(delhi));
    assertTrue(new GstinValidator(Set.of("07")).validate(delhi).structurallyValid());
  }

  @Test
  void panPartMustBeFiveLettersFourDigitsOneLetter() {
    assertEquals(List.of(CheckId.GSTIN_003), checks(Fixtures.gstin("27AB1XD1234E1Z")));
    assertEquals(List.of(CheckId.GSTIN_003), checks(Fixtures.gstin("27ABCXD12A4E1Z")));
    assertEquals(List.of(CheckId.GSTIN_003), checks(Fixtures.gstin("27ABCXD123451Z")));
  }

  @Test
  void entityNumberZeroIsRejectedButLettersAndDigitsAreAccepted() {
    assertEquals(List.of(CheckId.GSTIN_004), checks(Fixtures.gstin("27ABCXD1234E0Z")));
    assertTrue(validator.validate(Fixtures.gstin("27ABCXD1234E9Z")).structurallyValid());
    assertTrue(validator.validate(Fixtures.gstin("27ABCXD1234EAZ")).structurallyValid());
  }

  @Test
  void fourteenthCharacterMustBeZ() {
    assertEquals(List.of(CheckId.GSTIN_005), checks(Fixtures.gstin("27ABCXD1234E1Y")));
  }

  @Test
  void wrongCheckCharacterIsRejectedAndTheMessageNamesTheExpectedOne() {
    char right = VALID.charAt(14);
    char wrong = right == 'A' ? 'B' : 'A';
    GstinResult result = validator.validate(VALID.substring(0, 14) + wrong);
    assertEquals(List.of(CheckId.GSTIN_006), checks(VALID.substring(0, 14) + wrong));
    assertTrue(result.problems().get(0).message().contains("'" + right + "'"));
    assertFalse(result.structurallyValid());
  }

  @Test
  void everySingleCharacterSubstitutionIsRejected() {
    String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    for (int position = 0; position < VALID.length(); position++) {
      for (char replacement : alphabet.toCharArray()) {
        if (replacement == VALID.charAt(position)) {
          continue;
        }
        String mutated = VALID.substring(0, position) + replacement + VALID.substring(position + 1);
        assertFalse(
            validator.validate(mutated).structurallyValid(),
            "Should reject " + mutated + " (position " + (position + 1) + ")");
      }
    }
  }

  @Test
  void severalProblemsAreReportedTogether() {
    // Unknown state, entity 0, 'Y' instead of 'Z' and a wrong check character.
    String body = "99ABCXD1234E0Y";
    char wrong = GstinChecksum.compute(body) == 'A' ? 'B' : 'A';
    assertEquals(
        List.of(CheckId.GSTIN_002, CheckId.GSTIN_004, CheckId.GSTIN_005, CheckId.GSTIN_006),
        checks(body + wrong));
  }

  @Test
  void lowerCaseAndSpacesAreAWarningAndTheValueIsStillChecked() {
    GstinResult result = validator.validate("  " + VALID.toLowerCase() + " ");
    assertEquals(List.of(CheckId.GSTIN_007), checks("  " + VALID.toLowerCase() + " "));
    assertTrue(result.structurallyValid(), "a warning alone does not make it invalid");
    assertEquals(VALID, result.normalized());
  }

  @Test
  void lowerCaseDoesNotHideARealError() {
    String wrong = (VALID.substring(0, 14) + (VALID.charAt(14) == 'A' ? 'B' : 'A')).toLowerCase();
    assertEquals(List.of(CheckId.GSTIN_006, CheckId.GSTIN_007), checks(wrong));
  }

  @Test
  void resultKeepsTheOriginalInput() {
    assertEquals(" x ", validator.validate(" x ").input());
    assertEquals("X", validator.validate(" x ").normalized());
  }

  @Test
  void theNoticeSaysAPassIsNotProofOfRegistration() {
    assertTrue(GstinResult.STRUCTURAL_ONLY_NOTICE.contains("does not prove"));
    assertTrue(GstinResult.STRUCTURAL_ONLY_NOTICE.contains("registered or active"));
  }
}
