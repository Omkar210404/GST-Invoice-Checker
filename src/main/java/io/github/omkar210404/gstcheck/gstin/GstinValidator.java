package io.github.omkar210404.gstcheck.gstin;

import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks the structure of a GSTIN: length, state code, PAN-shaped middle, entity number, the fixed
 * {@code Z} and the check character.
 *
 * <p>This is an offline structural check. It never contacts the GST portal and cannot tell whether
 * a number is registered or active. It never throws, whatever the input.
 */
public final class GstinValidator {

  public static final int LENGTH = 15;

  private static final Pattern PAN_SHAPE = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]");
  private static final Pattern STATE_SHAPE = Pattern.compile("[0-9]{2}");

  private final Set<String> stateCodes;

  /**
   * @param stateCodes the two-digit state codes to accept, taken from the config
   */
  public GstinValidator(Set<String> stateCodes) {
    this.stateCodes = Set.copyOf(stateCodes);
  }

  public GstinResult validate(String raw) {
    String input = raw == null ? "" : raw;
    String gstin = InvoiceLine.normalizeGstin(input);
    List<GstinProblem> problems = new ArrayList<>();

    if (gstin.length() != LENGTH || !GstinChecksum.isAlphabetOnly(gstin)) {
      problems.add(
          new GstinProblem(
              CheckId.GSTIN_001,
              describeShapeProblem(gstin),
              "Re-enter the GSTIN from the registration certificate or the supplier's invoice."
                  + " It is exactly 15 characters, using only A-Z and 0-9."));
      addCanonicalWarning(input, gstin, problems);
      return new GstinResult(input, gstin, problems);
    }

    String state = gstin.substring(0, 2);
    if (!STATE_SHAPE.matcher(state).matches()) {
      problems.add(
          new GstinProblem(
              CheckId.GSTIN_002,
              "The first two characters '" + state + "' should be a two-digit state code.",
              "Check the first two characters against the state the business is registered in."));
    } else if (!stateCodes.contains(state)) {
      problems.add(
          new GstinProblem(
              CheckId.GSTIN_002,
              "State code '" + state + "' is not in the list of state codes in the config.",
              "Check the first two digits. If the code is genuine, add it to 'states' in the"
                  + " config file."));
    }

    String pan = gstin.substring(2, 12);
    if (!PAN_SHAPE.matcher(pan).matches()) {
      problems.add(
          new GstinProblem(
              CheckId.GSTIN_003,
              "Characters 3 to 12 '"
                  + pan
                  + "' are not PAN-shaped (5 letters, then 4 digits, then 1 letter).",
              "Compare characters 3 to 12 with the PAN of the business. Look for a letter typed"
                  + " as a digit, such as O and 0 or I and 1."));
    }

    char entity = gstin.charAt(12);
    if (entity == '0') {
      problems.add(
          new GstinProblem(
              CheckId.GSTIN_004,
              "Character 13 (the entity number) is '0'. It should be 1 to 9 or A to Z.",
              "Check character 13. It counts the registrations under the same PAN in a state,"
                  + " starting from 1."));
    }

    char fixed = gstin.charAt(13);
    if (fixed != 'Z') {
      problems.add(
          new GstinProblem(
              CheckId.GSTIN_005,
              "Character 14 is '" + fixed + "'. It is expected to be 'Z'.",
              "Check character 14. If your GSTIN genuinely has another letter here, downgrade"
                  + " GSTIN-005 to a warning in the config."));
    }

    char expected = GstinChecksum.compute(gstin.substring(0, 14));
    char actual = gstin.charAt(14);
    if (expected != actual) {
      problems.add(
          new GstinProblem(
              CheckId.GSTIN_006,
              "The check character is '"
                  + actual
                  + "' but the first 14 characters give '"
                  + expected
                  + "'. One or more characters are probably mistyped.",
              "Re-enter the whole GSTIN from the source document. Do not just change the last"
                  + " character: the mistake is usually somewhere in the first 14."));
    }

    addCanonicalWarning(input, gstin, problems);
    return new GstinResult(input, gstin, problems);
  }

  private static void addCanonicalWarning(
      String input, String normalized, List<GstinProblem> problems) {
    if (!input.equals(normalized)) {
      problems.add(
          new GstinProblem(
              CheckId.GSTIN_007,
              "The GSTIN has lower-case letters or surrounding spaces. It was checked as '"
                  + abbreviate(normalized)
                  + "'.",
              "Store GSTINs in upper case with no spaces."));
    }
  }

  private static String describeShapeProblem(String gstin) {
    if (gstin.isEmpty()) {
      return "The GSTIN is empty.";
    }
    if (gstin.length() != LENGTH) {
      return "The GSTIN has " + gstin.length() + " characters. It should have exactly 15.";
    }
    return "The GSTIN contains characters other than A-Z and 0-9.";
  }

  private static String abbreviate(String text) {
    return text.length() <= 40 ? text : text.substring(0, 40) + "...";
  }
}
