package io.github.omkar210404.gstcheck.model;

import java.time.LocalDate;
import java.util.Locale;

/**
 * One row of the input CSV. Text is kept as read; numbers are {@link Amount}s that remember their
 * original text. {@code invoiceDate} is null when the date text could not be parsed.
 *
 * @param lineNo the row as a spreadsheet shows it (the header is row 1)
 */
public record InvoiceLine(
    int lineNo,
    String invoiceNo,
    String invoiceDateText,
    LocalDate invoiceDate,
    String supplierGstin,
    String buyerGstin,
    String placeOfSupply,
    String hsnOrSac,
    String description,
    Amount quantity,
    String unit,
    Amount unitPrice,
    Amount taxableValue,
    Amount rate,
    Amount cgst,
    Amount sgst,
    Amount igst,
    Amount cess,
    Amount lineTotal,
    String supplierName,
    String buyerName) {

  public InvoiceLine {
    invoiceNo = text(invoiceNo);
    invoiceDateText = text(invoiceDateText);
    supplierGstin = text(supplierGstin);
    buyerGstin = text(buyerGstin);
    placeOfSupply = text(placeOfSupply);
    hsnOrSac = text(hsnOrSac);
    description = text(description);
    unit = text(unit);
    supplierName = text(supplierName);
    buyerName = text(buyerName);
    quantity = amount(quantity);
    unitPrice = amount(unitPrice);
    taxableValue = amount(taxableValue);
    rate = amount(rate);
    cgst = amount(cgst);
    sgst = amount(sgst);
    igst = amount(igst);
    cess = amount(cess);
    lineTotal = amount(lineTotal);
  }

  private static String text(String value) {
    return value == null ? "" : value;
  }

  private static Amount amount(Amount value) {
    return value == null ? Amount.blank() : value;
  }

  /** The supplier GSTIN trimmed and upper-cased, the form used for grouping and comparison. */
  public String supplierGstinNormalized() {
    return normalizeGstin(supplierGstin);
  }

  /** The buyer GSTIN trimmed and upper-cased. Empty for an unregistered buyer. */
  public String buyerGstinNormalized() {
    return normalizeGstin(buyerGstin);
  }

  public static String normalizeGstin(String raw) {
    return raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    Builder b = new Builder();
    b.lineNo = lineNo;
    b.invoiceNo = invoiceNo;
    b.invoiceDateText = invoiceDateText;
    b.invoiceDate = invoiceDate;
    b.supplierGstin = supplierGstin;
    b.buyerGstin = buyerGstin;
    b.placeOfSupply = placeOfSupply;
    b.hsnOrSac = hsnOrSac;
    b.description = description;
    b.quantity = quantity;
    b.unit = unit;
    b.unitPrice = unitPrice;
    b.taxableValue = taxableValue;
    b.rate = rate;
    b.cgst = cgst;
    b.sgst = sgst;
    b.igst = igst;
    b.cess = cess;
    b.lineTotal = lineTotal;
    b.supplierName = supplierName;
    b.buyerName = buyerName;
    return b;
  }

  /** Mutable builder; the record it builds is immutable. */
  public static final class Builder {
    private int lineNo;
    private String invoiceNo;
    private String invoiceDateText;
    private LocalDate invoiceDate;
    private String supplierGstin;
    private String buyerGstin;
    private String placeOfSupply;
    private String hsnOrSac;
    private String description;
    private Amount quantity;
    private String unit;
    private Amount unitPrice;
    private Amount taxableValue;
    private Amount rate;
    private Amount cgst;
    private Amount sgst;
    private Amount igst;
    private Amount cess;
    private Amount lineTotal;
    private String supplierName;
    private String buyerName;

    private Builder() {}

    public Builder lineNo(int value) {
      this.lineNo = value;
      return this;
    }

    public Builder invoiceNo(String value) {
      this.invoiceNo = value;
      return this;
    }

    /** Sets the date text exactly as it appeared in the file. */
    public Builder invoiceDateText(String value) {
      this.invoiceDateText = value;
      return this;
    }

    /** Sets the parsed date, or null when the text is not a valid date. */
    public Builder invoiceDate(LocalDate value) {
      this.invoiceDate = value;
      return this;
    }

    public Builder supplierGstin(String value) {
      this.supplierGstin = value;
      return this;
    }

    public Builder buyerGstin(String value) {
      this.buyerGstin = value;
      return this;
    }

    public Builder placeOfSupply(String value) {
      this.placeOfSupply = value;
      return this;
    }

    public Builder hsnOrSac(String value) {
      this.hsnOrSac = value;
      return this;
    }

    public Builder description(String value) {
      this.description = value;
      return this;
    }

    public Builder quantity(String value) {
      this.quantity = Amount.parse(value);
      return this;
    }

    public Builder unit(String value) {
      this.unit = value;
      return this;
    }

    public Builder unitPrice(String value) {
      this.unitPrice = Amount.parse(value);
      return this;
    }

    public Builder taxableValue(String value) {
      this.taxableValue = Amount.parse(value);
      return this;
    }

    public Builder rate(String value) {
      this.rate = Amount.parse(value);
      return this;
    }

    public Builder cgst(String value) {
      this.cgst = Amount.parse(value);
      return this;
    }

    public Builder sgst(String value) {
      this.sgst = Amount.parse(value);
      return this;
    }

    public Builder igst(String value) {
      this.igst = Amount.parse(value);
      return this;
    }

    public Builder cess(String value) {
      this.cess = Amount.parse(value);
      return this;
    }

    public Builder lineTotal(String value) {
      this.lineTotal = Amount.parse(value);
      return this;
    }

    public Builder supplierName(String value) {
      this.supplierName = value;
      return this;
    }

    public Builder buyerName(String value) {
      this.buyerName = value;
      return this;
    }

    public InvoiceLine build() {
      return new InvoiceLine(
          lineNo,
          invoiceNo,
          invoiceDateText,
          invoiceDate,
          supplierGstin,
          buyerGstin,
          placeOfSupply,
          hsnOrSac,
          description,
          quantity,
          unit,
          unitPrice,
          taxableValue,
          rate,
          cgst,
          sgst,
          igst,
          cess,
          lineTotal,
          supplierName,
          buyerName);
    }
  }
}
