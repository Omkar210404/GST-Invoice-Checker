package io.github.omkar210404.gstcheck.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.omkar210404.gstcheck.config.CheckerConfig.StateInfo;
import io.github.omkar210404.gstcheck.config.CheckerConfig.Tolerance;
import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Column;
import io.github.omkar210404.gstcheck.model.InputProblemException;
import io.github.omkar210404.gstcheck.model.Severity;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads and validates the YAML config. Unknown keys and bad values are rejected with a message that
 * says which key is wrong, rather than being silently ignored.
 */
public final class ConfigLoader {

  /** The example config bundled in the JAR. The {@code sample} command writes this file out. */
  public static final String EXAMPLE_RESOURCE = "/example/gst-checker.yml";

  private static final List<String> DEFAULT_DATE_FORMATS =
      List.of("yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy");
  private static final List<Integer> DEFAULT_HSN_LENGTHS = List.of(4, 6, 8);
  private static final BigDecimal DEFAULT_TOLERANCE = new BigDecimal("0.01");
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private static final ObjectMapper YAML =
      new ObjectMapper(new YAMLFactory())
          .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

  private ConfigLoader() {}

  /** The file as written by the user, before validation. Missing sections are null. */
  record RawConfig(
      String financialYear,
      List<String> dateFormats,
      RawHsn hsn,
      RawRates rates,
      RawTolerance tolerance,
      Map<String, RawState> states,
      List<List<String>> equivalentStateCodes,
      Map<String, String> columns,
      RawChecks checks) {}

  record RawHsn(List<Integer> allowedLengths) {}

  record RawRates(List<BigDecimal> allowedSlabs) {}

  record RawTolerance(BigDecimal taxAmount, BigDecimal lineTotal, BigDecimal quantityPrice) {}

  record RawState(String name, Boolean utgst) {}

  record RawChecks(List<String> disabled, Map<String, String> severity) {}

  /**
   * Loads a config file.
   *
   * @throws InputProblemException if the file is missing, unreadable or invalid
   */
  public static CheckerConfig load(Path file) {
    String text;
    try {
      text = Files.readString(file, StandardCharsets.UTF_8);
    } catch (NoSuchFileException e) {
      throw new InputProblemException("Config file not found: " + file, e);
    } catch (IOException e) {
      throw new InputProblemException("Could not read config file " + file + ": " + e, e);
    }
    return parse(text, file.toString());
  }

  /** Loads the example config bundled in the JAR. */
  public static CheckerConfig loadExample() {
    return parse(exampleText(), "built-in example config");
  }

  /** The text of the bundled example config. */
  public static String exampleText() {
    try (InputStream in = ConfigLoader.class.getResourceAsStream(EXAMPLE_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing resource " + EXAMPLE_RESOURCE);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Could not read resource " + EXAMPLE_RESOURCE, e);
    }
  }

  /**
   * Parses and validates config text.
   *
   * @param source a name for error messages, usually the file path
   */
  public static CheckerConfig parse(String yaml, String source) {
    RawConfig raw;
    try {
      raw = YAML.readValue(yaml, RawConfig.class);
    } catch (JsonProcessingException e) {
      throw new InputProblemException(
          "Config " + source + " could not be read: " + e.getOriginalMessage() + location(e), e);
    }
    if (raw == null) {
      throw new InputProblemException("Config " + source + " is empty.");
    }
    try {
      return validate(raw);
    } catch (InputProblemException e) {
      throw new InputProblemException("Config " + source + ": " + e.getMessage(), e);
    }
  }

  private static String location(JsonProcessingException e) {
    return e.getLocation() == null
        ? ""
        : " (line "
            + e.getLocation().getLineNr()
            + ", column "
            + e.getLocation().getColumnNr()
            + ")";
  }

  private static CheckerConfig validate(RawConfig raw) {
    if (raw.financialYear() == null || raw.financialYear().isBlank()) {
      throw new InputProblemException("financial_year is required, for example \"2026-27\".");
    }
    FinancialYear year = FinancialYear.parse(raw.financialYear());

    List<String> dateFormats =
        raw.dateFormats() == null || raw.dateFormats().isEmpty()
            ? DEFAULT_DATE_FORMATS
            : raw.dateFormats();
    for (String format : dateFormats) {
      if (format == null || format.isBlank()) {
        throw new InputProblemException("date_formats must not contain an empty entry.");
      }
    }
    // Building the parser validates every pattern.
    new DateParser(dateFormats);

    List<Integer> hsnLengths =
        raw.hsn() == null || raw.hsn().allowedLengths() == null
            ? DEFAULT_HSN_LENGTHS
            : raw.hsn().allowedLengths();
    if (hsnLengths.isEmpty()) {
      throw new InputProblemException("hsn.allowed_lengths must list at least one length.");
    }
    for (Integer length : hsnLengths) {
      if (length == null || length < 1 || length > 20) {
        throw new InputProblemException(
            "hsn.allowed_lengths entry '" + length + "' must be between 1 and 20.");
      }
    }

    if (raw.rates() == null
        || raw.rates().allowedSlabs() == null
        || raw.rates().allowedSlabs().isEmpty()) {
      throw new InputProblemException(
          "rates.allowed_slabs is required. List the tax rates (in percent) you accept, after"
              + " verifying them against official GST notifications.");
    }
    for (BigDecimal slab : raw.rates().allowedSlabs()) {
      if (slab == null || slab.signum() < 0 || slab.compareTo(HUNDRED) > 0) {
        throw new InputProblemException(
            "rates.allowed_slabs entry '" + slab + "' must be a percentage between 0 and 100.");
      }
    }

    Tolerance tolerance = tolerance(raw.tolerance());
    Map<String, StateInfo> states = states(raw.states());
    List<Set<String>> equivalents = equivalents(raw.equivalentStateCodes(), states);
    Map<Column, String> columns = columns(raw.columns());

    Set<CheckId> disabled = EnumSet.noneOf(CheckId.class);
    Map<CheckId, Severity> overrides = new EnumMap<>(CheckId.class);
    if (raw.checks() != null) {
      if (raw.checks().disabled() != null) {
        for (String id : raw.checks().disabled()) {
          disabled.add(checkId(id, "checks.disabled"));
        }
      }
      if (raw.checks().severity() != null) {
        for (Map.Entry<String, String> entry : raw.checks().severity().entrySet()) {
          CheckId check = checkId(entry.getKey(), "checks.severity");
          Severity severity =
              Severity.fromLabel(entry.getValue())
                  .orElseThrow(
                      () ->
                          new InputProblemException(
                              "checks.severity for "
                                  + check.id()
                                  + " is '"
                                  + entry.getValue()
                                  + "'. Use \"error\" or \"warning\"."));
          overrides.put(check, severity);
        }
      }
    }

    return new CheckerConfig(
        year,
        dateFormats,
        new LinkedHashSet<>(hsnLengths),
        raw.rates().allowedSlabs(),
        tolerance,
        states,
        equivalents,
        columns,
        disabled,
        overrides);
  }

  private static Tolerance tolerance(RawTolerance raw) {
    if (raw == null) {
      return new Tolerance(DEFAULT_TOLERANCE, DEFAULT_TOLERANCE, DEFAULT_TOLERANCE);
    }
    return new Tolerance(
        toleranceValue(raw.taxAmount(), "tolerance.tax_amount"),
        toleranceValue(raw.lineTotal(), "tolerance.line_total"),
        toleranceValue(raw.quantityPrice(), "tolerance.quantity_price"));
  }

  private static BigDecimal toleranceValue(BigDecimal value, String key) {
    if (value == null) {
      return DEFAULT_TOLERANCE;
    }
    if (value.signum() < 0) {
      throw new InputProblemException(key + " must not be negative.");
    }
    return value;
  }

  private static Map<String, StateInfo> states(Map<String, RawState> raw) {
    if (raw == null || raw.isEmpty()) {
      throw new InputProblemException(
          "states is required. List the two-digit GST state codes you accept.");
    }
    Map<String, StateInfo> states = new LinkedHashMap<>();
    for (Map.Entry<String, RawState> entry : raw.entrySet()) {
      String code = entry.getKey();
      if (code == null || !code.matches("[0-9]{2}")) {
        throw new InputProblemException(
            "states key '"
                + code
                + "' must be two digits in quotes, for example \"07\". Without quotes YAML drops"
                + " the leading zero.");
      }
      RawState state = entry.getValue();
      if (state == null || state.name() == null || state.name().isBlank()) {
        throw new InputProblemException("states." + code + " needs a name.");
      }
      states.put(code, new StateInfo(state.name().strip(), Boolean.TRUE.equals(state.utgst())));
    }
    return states;
  }

  private static List<Set<String>> equivalents(
      List<List<String>> raw, Map<String, StateInfo> states) {
    List<Set<String>> groups = new ArrayList<>();
    if (raw == null) {
      return groups;
    }
    for (List<String> group : raw) {
      if (group == null || group.size() < 2) {
        throw new InputProblemException(
            "equivalent_state_codes: each group needs at least two codes, for example"
                + " [\"25\", \"26\"].");
      }
      for (String code : group) {
        if (!states.containsKey(code)) {
          throw new InputProblemException(
              "equivalent_state_codes: code '" + code + "' is not listed under states.");
        }
      }
      groups.add(new HashSet<>(group));
    }
    return groups;
  }

  private static Map<Column, String> columns(Map<String, String> raw) {
    Map<Column, String> columns = new EnumMap<>(Column.class);
    if (raw == null) {
      return columns;
    }
    for (Map.Entry<String, String> entry : raw.entrySet()) {
      Column column =
          Column.fromKey(entry.getKey())
              .orElseThrow(
                  () ->
                      new InputProblemException(
                          "columns: '" + entry.getKey() + "' is not a column this tool knows."));
      if (entry.getValue() == null || entry.getValue().isBlank()) {
        throw new InputProblemException("columns." + column.key() + " must not be empty.");
      }
      columns.put(column, entry.getValue().strip());
    }
    return columns;
  }

  private static CheckId checkId(String id, String key) {
    return CheckId.fromId(id)
        .orElseThrow(
            () -> new InputProblemException(key + ": '" + id + "' is not a known check ID."));
  }
}
