package io.github.omkar210404.gstcheck.config;

import io.github.omkar210404.gstcheck.model.InputProblemException;
import java.time.LocalDate;
import java.time.Month;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An Indian financial year: 1 April of {@code startYear} to 31 March of the next year.
 *
 * @param startYear the calendar year the financial year starts in
 */
public record FinancialYear(int startYear) {

  private static final Pattern LABEL = Pattern.compile("(\\d{4})-(\\d{2})");

  /**
   * Parses a label such as {@code 2026-27}.
   *
   * @throws InputProblemException if the label is malformed or the two years are not consecutive
   */
  public static FinancialYear parse(String label) {
    String text = label == null ? "" : label.strip();
    Matcher matcher = LABEL.matcher(text);
    if (!matcher.matches()) {
      throw new InputProblemException(
          "financial_year '" + text + "' is not valid. Write it like \"2026-27\".");
    }
    int start = Integer.parseInt(matcher.group(1));
    int endSuffix = Integer.parseInt(matcher.group(2));
    if ((start + 1) % 100 != endSuffix) {
      throw new InputProblemException(
          "financial_year '"
              + text
              + "' is not valid: the second part must be the year after the first, like"
              + " \"2026-27\".");
    }
    return new FinancialYear(start);
  }

  /** The financial year a date falls in. */
  public static FinancialYear containing(LocalDate date) {
    return new FinancialYear(
        date.getMonthValue() >= Month.APRIL.getValue() ? date.getYear() : date.getYear() - 1);
  }

  public LocalDate start() {
    return LocalDate.of(startYear, Month.APRIL, 1);
  }

  public LocalDate end() {
    return LocalDate.of(startYear + 1, Month.MARCH, 31);
  }

  public boolean contains(LocalDate date) {
    return !date.isBefore(start()) && !date.isAfter(end());
  }

  /** The label form, for example {@code 2026-27}. */
  public String label() {
    return String.format("%04d-%02d", startYear, (startYear + 1) % 100);
  }
}
