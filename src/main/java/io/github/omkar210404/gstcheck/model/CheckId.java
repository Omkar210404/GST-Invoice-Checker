package io.github.omkar210404.gstcheck.model;

import java.util.Optional;

/**
 * The catalogue of every check the tool can raise: its public ID, default severity and a short
 * title. The README table is tested against this enum, so the two cannot drift apart.
 */
public enum CheckId {
  DATA_001("DATA-001", Severity.ERROR, "Required value is missing"),
  DATA_002("DATA-002", Severity.ERROR, "Value is not a plain decimal number"),

  GSTIN_001("GSTIN-001", Severity.ERROR, "GSTIN is not 15 letters and digits"),
  GSTIN_002("GSTIN-002", Severity.ERROR, "GSTIN state code is not in the configured list"),
  GSTIN_003("GSTIN-003", Severity.ERROR, "GSTIN characters 3 to 12 are not PAN-shaped"),
  GSTIN_004("GSTIN-004", Severity.ERROR, "GSTIN entity number (character 13) is not valid"),
  GSTIN_005("GSTIN-005", Severity.ERROR, "GSTIN character 14 is not Z"),
  GSTIN_006("GSTIN-006", Severity.ERROR, "GSTIN check character does not match"),
  GSTIN_007("GSTIN-007", Severity.WARNING, "GSTIN has lower-case letters or surrounding spaces"),

  INV_001("INV-001", Severity.ERROR, "Invoice number is longer than 16 characters"),
  INV_002("INV-002", Severity.ERROR, "Invoice number has characters that are not allowed"),
  INV_003("INV-003", Severity.ERROR, "Invoice number reused with different invoice details"),
  INV_004("INV-004", Severity.WARNING, "Invoice numbers differ only by letter case"),

  DATE_001("DATE-001", Severity.ERROR, "Invoice date is not a valid date"),
  DATE_002("DATE-002", Severity.ERROR, "Invoice date is in the future"),
  DATE_003("DATE-003", Severity.ERROR, "Invoice date is outside the configured financial year"),

  HSN_001("HSN-001", Severity.ERROR, "HSN/SAC is not digits only"),
  HSN_002("HSN-002", Severity.ERROR, "HSN/SAC length is not allowed"),

  POS_001("POS-001", Severity.ERROR, "Place of supply is not a configured state code"),

  TAX_001("TAX-001", Severity.ERROR, "IGST charged on an intra-state supply"),
  TAX_002("TAX-002", Severity.ERROR, "CGST/SGST charged on an inter-state supply"),
  TAX_003("TAX-003", Severity.ERROR, "IGST mixed with CGST/SGST on one line"),
  TAX_004("TAX-004", Severity.ERROR, "CGST and SGST are not equal"),

  MATH_001("MATH-001", Severity.ERROR, "Tax amount does not equal taxable value x rate"),
  MATH_002("MATH-002", Severity.ERROR, "Line total does not equal taxable value + taxes + cess"),
  MATH_003("MATH-003", Severity.WARNING, "Taxable value does not equal quantity x unit price"),
  MATH_004("MATH-004", Severity.ERROR, "Negative amount"),
  MATH_005("MATH-005", Severity.WARNING, "Zero amount"),

  RATE_001("RATE-001", Severity.ERROR, "Rate is not in the configured slab list");

  private final String id;
  private final Severity defaultSeverity;
  private final String title;

  CheckId(String id, Severity defaultSeverity, String title) {
    this.id = id;
    this.defaultSeverity = defaultSeverity;
    this.title = title;
  }

  /** The public ID shown in every report, for example {@code GSTIN-006}. */
  public String id() {
    return id;
  }

  public Severity defaultSeverity() {
    return defaultSeverity;
  }

  public String title() {
    return title;
  }

  /** Looks a check up by its public ID, ignoring case and surrounding spaces. */
  public static Optional<CheckId> fromId(String id) {
    if (id == null) {
      return Optional.empty();
    }
    for (CheckId check : values()) {
      if (check.id.equalsIgnoreCase(id.strip())) {
        return Optional.of(check);
      }
    }
    return Optional.empty();
  }
}
