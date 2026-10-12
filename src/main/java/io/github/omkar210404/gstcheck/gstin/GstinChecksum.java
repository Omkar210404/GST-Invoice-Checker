package io.github.omkar210404.gstcheck.gstin;

/**
 * The GSTIN check character: a mod-36 scheme over the first 14 characters.
 *
 * <p>Each character has a value from 0 to 35 ({@code 0-9} then {@code A-Z}). Values are multiplied
 * by a weight that alternates 1, 2, 1, 2... from the left. Each product is folded to {@code product
 * / 36 + product % 36}, the folded values are summed, and the check value is {@code (36 - sum % 36)
 * % 36}, written back as a character.
 */
public final class GstinChecksum {

  static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final int BASE = ALPHABET.length();
  private static final int BODY_LENGTH = 14;

  private GstinChecksum() {}

  /**
   * Computes the check character for the first 14 characters of a GSTIN.
   *
   * @throws IllegalArgumentException if the body is not exactly 14 characters of 0-9 and A-Z
   */
  public static char compute(String body) {
    if (body == null || body.length() != BODY_LENGTH) {
      throw new IllegalArgumentException("GSTIN body must be exactly 14 characters");
    }
    int sum = 0;
    for (int i = 0; i < BODY_LENGTH; i++) {
      int value = ALPHABET.indexOf(body.charAt(i));
      if (value < 0) {
        throw new IllegalArgumentException("GSTIN body may only contain 0-9 and A-Z");
      }
      int weight = (i % 2 == 0) ? 1 : 2;
      int product = value * weight;
      sum += product / BASE + product % BASE;
    }
    return ALPHABET.charAt((BASE - sum % BASE) % BASE);
  }

  /** True when every character is an upper-case ASCII letter or digit. */
  static boolean isAlphabetOnly(String text) {
    for (int i = 0; i < text.length(); i++) {
      if (ALPHABET.indexOf(text.charAt(i)) < 0) {
        return false;
      }
    }
    return true;
  }
}
