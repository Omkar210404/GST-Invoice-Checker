package io.github.omkar210404.gstcheck.gstin;

import java.util.random.RandomGenerator;

/**
 * Generates SYNTHETIC GSTINs for sample data and tests. They have the right shape and a valid check
 * character, and nothing else: they are not issued to anyone.
 *
 * <p>A generated number could still match a real registration by coincidence. Never present one as
 * a real identity.
 */
public final class SyntheticGstin {

  private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

  /**
   * The fourth PAN letter normally says what kind of holder the PAN belongs to. 'X' is used here
   * because it is not one of the usual holder-type letters, which lowers the chance of matching a
   * real PAN. It does not remove it.
   */
  private static final char SYNTHETIC_HOLDER_LETTER = 'X';

  private SyntheticGstin() {}

  /**
   * @param random source of randomness; pass a seeded generator for repeatable output
   * @param stateCode two digits, for example {@code "27"}
   */
  public static String generate(RandomGenerator random, String stateCode) {
    if (stateCode == null || !stateCode.matches("[0-9]{2}")) {
      throw new IllegalArgumentException("State code must be two digits");
    }
    StringBuilder body = new StringBuilder(14).append(stateCode);
    for (int i = 0; i < 3; i++) {
      body.append(letter(random));
    }
    body.append(SYNTHETIC_HOLDER_LETTER).append(letter(random));
    for (int i = 0; i < 4; i++) {
      body.append((char) ('0' + random.nextInt(10)));
    }
    body.append(letter(random)).append('1').append('Z');
    return body.toString() + GstinChecksum.compute(body.toString());
  }

  private static char letter(RandomGenerator random) {
    return LETTERS.charAt(random.nextInt(LETTERS.length()));
  }
}
