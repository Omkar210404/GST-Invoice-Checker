package io.github.omkar210404.gstcheck.gstin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.omkar210404.gstcheck.model.Amount;
import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import java.util.Random;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.CharRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

/** Randomised tests: the validator must cope with anything a CSV cell can contain. */
class GstinValidatorPropertyTest {

  private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final Set<String> STATES = Set.of("04", "07", "24", "27", "29");

  private final GstinValidator validator = new GstinValidator(STATES);

  @Property(tries = 2000)
  void neverThrowsOnArbitraryText(@ForAll String anything) {
    GstinResult result = validator.validate(anything);
    assertNotNull(result);
    assertNotNull(result.problems());
    assertEquals(anything, result.input());
  }

  @Property(tries = 2000)
  void neverThrowsOnFifteenCharactersFromTheGstinAlphabet(
      @ForAll @StringLength(15) @CharRange(from = '0', to = '9') @CharRange(from = 'A', to = 'Z')
          String candidate) {
    GstinResult result = validator.validate(candidate);
    assertFalse(
        result.has(CheckId.GSTIN_001), "15 alphabet characters always pass the shape check");
  }

  @Property(tries = 1000)
  void arbitraryTextIsOnlyValidWhenItIsFifteenAlphabetCharacters(@ForAll String anything) {
    if (validator.validate(anything).structurallyValid()) {
      String normalized = InvoiceLine.normalizeGstin(anything);
      assertEquals(15, normalized.length());
      assertTrue(normalized.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0));
    }
  }

  @Property(tries = 500)
  void generatedSyntheticGstinsAreStructurallyValid(@ForAll long seed) {
    String gstin = SyntheticGstin.generate(new Random(seed), "27");
    assertTrue(validator.validate(gstin).structurallyValid(), gstin);
  }

  @Property(tries = 2000)
  void anySingleCharacterSubstitutionMakesAGeneratedGstinInvalid(
      @ForAll long seed,
      @ForAll @IntRange(min = 0, max = 14) int position,
      @ForAll @IntRange(min = 0, max = 35) int replacementIndex) {
    String gstin = SyntheticGstin.generate(new Random(seed), "27");
    char replacement = ALPHABET.charAt(replacementIndex);
    if (replacement == gstin.charAt(position)) {
      return;
    }
    String mutated = gstin.substring(0, position) + replacement + gstin.substring(position + 1);
    assertFalse(validator.validate(mutated).structurallyValid(), mutated);
  }

  @Property(tries = 2000)
  void amountParsingNeverThrows(@ForAll String anything) {
    Amount amount = Amount.parse(anything);
    assertNotNull(amount);
    // Exactly one of the three states holds.
    int states =
        (amount.isBlank() ? 1 : 0) + (amount.isInvalid() ? 1 : 0) + (amount.isPresent() ? 1 : 0);
    assertEquals(1, states);
  }
}
