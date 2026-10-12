package io.github.omkar210404.gstcheck.config;

import io.github.omkar210404.gstcheck.model.InputProblemException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Parses invoice dates strictly using the patterns from the config, tried in order. Strict means
 * impossible dates such as 30 February or 31 April are rejected instead of being rolled forward.
 */
public final class DateParser {

  private final List<DateTimeFormatter> formatters;

  /**
   * @param patterns patterns such as {@code yyyy-MM-dd} or {@code dd/MM/yyyy}
   * @throws InputProblemException if a pattern is not valid
   */
  public DateParser(List<String> patterns) {
    List<DateTimeFormatter> built = new ArrayList<>();
    for (String pattern : patterns) {
      try {
        // In strict mode 'y' (year of era) needs an era; 'u' (year) is what people mean by yyyy.
        String strictPattern = pattern.replace('y', 'u');
        built.add(
            new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(strictPattern)
                .toFormatter(Locale.ENGLISH)
                .withResolverStyle(ResolverStyle.STRICT));
      } catch (IllegalArgumentException e) {
        throw new InputProblemException(
            "date_formats entry '" + pattern + "' is not a valid date pattern: " + e.getMessage(),
            e);
      }
    }
    this.formatters = List.copyOf(built);
  }

  /** Returns the date, or empty when the text matches none of the patterns. Never throws. */
  public Optional<LocalDate> parse(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    String trimmed = text.strip();
    for (DateTimeFormatter formatter : formatters) {
      try {
        return Optional.of(LocalDate.parse(trimmed, formatter));
      } catch (DateTimeParseException e) {
        // Try the next pattern.
      }
    }
    return Optional.empty();
  }
}
