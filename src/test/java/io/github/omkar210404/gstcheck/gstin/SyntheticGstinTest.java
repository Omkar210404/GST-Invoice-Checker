package io.github.omkar210404.gstcheck.gstin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SyntheticGstinTest {

  @Test
  void sameSeedGivesTheSameGstin() {
    assertEquals(
        SyntheticGstin.generate(new Random(42), "27"),
        SyntheticGstin.generate(new Random(42), "27"));
  }

  @Test
  void differentSeedsGiveDifferentGstins() {
    assertNotEquals(
        SyntheticGstin.generate(new Random(1), "27"), SyntheticGstin.generate(new Random(2), "27"));
  }

  @Test
  void generatedGstinHasTheRequestedStateAndPassesTheValidator() {
    String gstin = SyntheticGstin.generate(new Random(7), "29");
    assertTrue(gstin.startsWith("29"));
    assertEquals(15, gstin.length());
    assertTrue(new GstinValidator(Set.of("29")).validate(gstin).structurallyValid());
  }

  @Test
  void fourthPanLetterIsTheSyntheticMarker() {
    // Character 6 of the GSTIN is the fourth PAN letter.
    assertEquals('X', SyntheticGstin.generate(new Random(7), "29").charAt(5));
  }

  @Test
  void rejectsABadStateCode() {
    assertThrows(IllegalArgumentException.class, () -> SyntheticGstin.generate(new Random(1), "7"));
    assertThrows(
        IllegalArgumentException.class, () -> SyntheticGstin.generate(new Random(1), "AB"));
    assertThrows(
        IllegalArgumentException.class, () -> SyntheticGstin.generate(new Random(1), null));
  }
}
