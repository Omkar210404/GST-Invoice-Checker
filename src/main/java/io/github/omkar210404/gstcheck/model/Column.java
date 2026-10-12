package io.github.omkar210404.gstcheck.model;

import java.util.Optional;

/** The canonical CSV columns. The config can map each one to a different header name. */
public enum Column {
  INVOICE_NO("invoice_no", true),
  INVOICE_DATE("invoice_date", true),
  SUPPLIER_GSTIN("supplier_gstin", true),
  BUYER_GSTIN("buyer_gstin", false),
  PLACE_OF_SUPPLY("place_of_supply_state_code", true),
  HSN_OR_SAC("hsn_or_sac", true),
  DESCRIPTION("description", false),
  QUANTITY("quantity", false),
  UNIT("unit", false),
  UNIT_PRICE("unit_price", false),
  TAXABLE_VALUE("taxable_value", true),
  RATE("rate", true),
  CGST("cgst", false),
  SGST("sgst", false),
  IGST("igst", false),
  CESS("cess", false),
  LINE_TOTAL("line_total", true),
  SUPPLIER_NAME("supplier_name", false),
  BUYER_NAME("buyer_name", false);

  private final String key;
  private final boolean required;

  Column(String key, boolean required) {
    this.key = key;
    this.required = required;
  }

  /** The canonical column name, as used in the CSV header and in issue reports. */
  public String key() {
    return key;
  }

  /** Whether the column must exist in the header and have a value on every row. */
  public boolean required() {
    return required;
  }

  public static Optional<Column> fromKey(String key) {
    if (key == null) {
      return Optional.empty();
    }
    for (Column column : values()) {
      if (column.key.equalsIgnoreCase(key.strip())) {
        return Optional.of(column);
      }
    }
    return Optional.empty();
  }
}
