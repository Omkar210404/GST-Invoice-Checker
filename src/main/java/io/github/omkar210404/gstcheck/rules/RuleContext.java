package io.github.omkar210404.gstcheck.rules;

import io.github.omkar210404.gstcheck.config.CheckerConfig;
import java.time.LocalDate;
import java.util.Objects;

/**
 * What a rule may consult besides the lines themselves.
 *
 * @param config the user's configuration
 * @param asOf the date treated as "today" when deciding whether an invoice date is in the future
 */
public record RuleContext(CheckerConfig config, LocalDate asOf) {
  public RuleContext {
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(asOf, "asOf");
  }
}
