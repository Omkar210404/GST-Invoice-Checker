package io.github.omkar210404.gstcheck.model;

import java.util.Locale;
import java.util.Optional;

/** How serious an issue is. Errors make the tool exit with code 1; warnings do not. */
public enum Severity {
  ERROR,
  WARNING;

  /** Lower-case name used in reports and in the config file. */
  public String label() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** Parses a label from the config file, ignoring case and surrounding spaces. */
  public static Optional<Severity> fromLabel(String label) {
    if (label == null) {
      return Optional.empty();
    }
    for (Severity severity : values()) {
      if (severity.name().equalsIgnoreCase(label.strip())) {
        return Optional.of(severity);
      }
    }
    return Optional.empty();
  }
}
