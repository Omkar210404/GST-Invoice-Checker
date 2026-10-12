package io.github.omkar210404.gstcheck.config;

import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Column;
import io.github.omkar210404.gstcheck.model.Severity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the checks need that is a matter of policy rather than code: the financial year, the
 * allowed rates, the state codes, tolerances and column names. All of it comes from the user's YAML
 * file; nothing here is a claim about current GST law.
 *
 * @param financialYear the year invoice dates must fall in
 * @param dateFormats accepted date patterns, tried in order
 * @param hsnAllowedLengths allowed HSN/SAC lengths
 * @param allowedSlabs allowed tax rates in percent
 * @param tolerance rounding tolerances
 * @param states state code to state details
 * @param equivalentStateCodes groups of codes treated as one territory for intra/inter-state
 * @param columns canonical column to the header used in the user's file
 * @param disabledChecks checks that are switched off
 * @param severityOverrides checks whose severity is changed
 */
public record CheckerConfig(
    FinancialYear financialYear,
    List<String> dateFormats,
    Set<Integer> hsnAllowedLengths,
    List<BigDecimal> allowedSlabs,
    Tolerance tolerance,
    Map<String, StateInfo> states,
    List<Set<String>> equivalentStateCodes,
    Map<Column, String> columns,
    Set<CheckId> disabledChecks,
    Map<CheckId, Severity> severityOverrides) {

  /**
   * Rounding tolerances in rupees. A difference equal to the tolerance passes.
   *
   * @param taxAmount for tax = taxable value x rate
   * @param lineTotal for line total = taxable value + taxes + cess
   * @param quantityPrice for taxable value = quantity x unit price
   */
  public record Tolerance(BigDecimal taxAmount, BigDecimal lineTotal, BigDecimal quantityPrice) {}

  /**
   * One state or union territory.
   *
   * @param name display name
   * @param utgst true when the territory charges UTGST where a state charges SGST; this only
   *     changes the wording of messages
   */
  public record StateInfo(String name, boolean utgst) {}

  public CheckerConfig {
    dateFormats = List.copyOf(dateFormats);
    hsnAllowedLengths = Set.copyOf(hsnAllowedLengths);
    allowedSlabs = List.copyOf(allowedSlabs);
    states = Map.copyOf(states);
    equivalentStateCodes = equivalentStateCodes.stream().map(Set::copyOf).toList();
    columns = Map.copyOf(columns);
    disabledChecks = Set.copyOf(disabledChecks);
    severityOverrides = Map.copyOf(severityOverrides);
  }

  public Set<String> stateCodes() {
    return states.keySet();
  }

  public boolean isKnownState(String code) {
    return states.containsKey(code);
  }

  /** A label such as {@code 27 Maharashtra}, or just the code when it is not configured. */
  public String describeState(String code) {
    StateInfo info = states.get(code);
    return info == null ? code : code + " " + info.name();
  }

  public boolean chargesUtgst(String code) {
    StateInfo info = states.get(code);
    return info != null && info.utgst();
  }

  /**
   * True when two state codes count as the same territory: either they are equal, or the config
   * lists them together under {@code equivalent_state_codes}.
   */
  public boolean sameTerritory(String first, String second) {
    if (first.equals(second)) {
      return true;
    }
    for (Set<String> group : equivalentStateCodes) {
      if (group.contains(first) && group.contains(second)) {
        return true;
      }
    }
    return false;
  }

  /** Compares by value, so 18, 18.0 and 18.00 are the same rate. */
  public boolean isSlabAllowed(BigDecimal rate) {
    for (BigDecimal slab : allowedSlabs) {
      if (slab.compareTo(rate) == 0) {
        return true;
      }
    }
    return false;
  }

  public boolean isDisabled(CheckId check) {
    return disabledChecks.contains(check);
  }

  public Severity severityOf(CheckId check) {
    return severityOverrides.getOrDefault(check, check.defaultSeverity());
  }

  /** The header to look for in the CSV for a canonical column. */
  public String headerFor(Column column) {
    return columns.getOrDefault(column, column.key());
  }

  public DateParser dateParser() {
    return new DateParser(dateFormats);
  }
}
