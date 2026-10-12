package io.github.omkar210404.gstcheck.gstin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GstinChecksumTest {

  /**
   * Worked by hand for the dummy body {@code 27AAAAA0000A1Z}.
   *
   * <pre>
   * char    2  7  A  A  A  A  A  0  0  0  0  A  1  Z
   * value   2  7 10 10 10 10 10  0  0  0  0 10  1 35
   * weight  1  2  1  2  1  2  1  2  1  2  1  2  1  2
   * product 2 14 10 20 10 20 10  0  0  0  0 20  1 70
   * folded  2 14 10 20 10 20 10  0  0  0  0 20  1 35   (70 -> 70/36 + 70%36 = 1 + 34)
   * sum = 142, 142 % 36 = 34, (36 - 34) % 36 = 2 -> '2'
   * </pre>
   */
  @Test
  void matchesHandWorkedExample() {
    assertEquals('2', GstinChecksum.compute("27AAAAA0000A1Z"));
  }

  @Test
  void allZeroBodyGivesZero() {
    // Sum is 0, and (36 - 0) % 36 must wrap to 0 rather than 36.
    assertEquals('0', GstinChecksum.compute("00000000000000"));
  }

  @Test
  void foldingAppliesWhenAProductReachesThirtySix() {
    // 'Z' (35) in an even position doubles to 70, which folds to 35. In an odd position it stays
    // 35. So both bodies sum to 35 and need check value 1.
    assertEquals('1', GstinChecksum.compute("0Z000000000000"));
    assertEquals('1', GstinChecksum.compute("Z0000000000000"));
  }

  @Test
  void weightAlternatesStartingWithOne() {
    // '1' in position 1 has weight 1 (sum 1 -> 'Z'); in position 2 it has weight 2 (sum 2 -> 'Y').
    assertEquals('Z', GstinChecksum.compute("10000000000000"));
    assertEquals('Y', GstinChecksum.compute("01000000000000"));
  }

  @Test
  void swappingTwoDifferentNeighboursChangesTheCheckCharacter() {
    assertNotEquals(
        GstinChecksum.compute("27ABCXD1234E1Z"), GstinChecksum.compute("27BACXD1234E1Z"));
  }

  @Test
  void rejectsBodiesOfTheWrongLength() {
    assertThrows(IllegalArgumentException.class, () -> GstinChecksum.compute("27AAAAA0000A1"));
    assertThrows(IllegalArgumentException.class, () -> GstinChecksum.compute("27AAAAA0000A1Z2"));
    assertThrows(IllegalArgumentException.class, () -> GstinChecksum.compute(""));
    assertThrows(IllegalArgumentException.class, () -> GstinChecksum.compute(null));
  }

  @Test
  void rejectsCharactersOutsideTheAlphabet() {
    assertThrows(IllegalArgumentException.class, () -> GstinChecksum.compute("27aaaaa0000a1z"));
    assertThrows(IllegalArgumentException.class, () -> GstinChecksum.compute("27AAAAA0000A1-"));
  }
}
