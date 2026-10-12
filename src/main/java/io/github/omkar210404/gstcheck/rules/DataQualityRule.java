package io.github.omkar210404.gstcheck.rules;

import io.github.omkar210404.gstcheck.model.Amount;
import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Column;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DATA-001 and DATA-002: required cells that are empty, and number cells that are not plain
 * decimals. Other rules skip a line when a value they need is missing or unparseable, so these two
 * checks are where such problems get reported, once.
 */
public final class DataQualityRule implements LineRule {

  @Override
  public List<CheckId> checks() {
    return List.of(CheckId.DATA_001, CheckId.DATA_002);
  }

  @Override
  public void check(InvoiceLine line, RuleContext context, List<Issue> issues) {
    Map<Column, String> requiredText = new LinkedHashMap<>();
    requiredText.put(Column.INVOICE_NO, line.invoiceNo());
    requiredText.put(Column.INVOICE_DATE, line.invoiceDateText());
    requiredText.put(Column.SUPPLIER_GSTIN, line.supplierGstin());
    requiredText.put(Column.PLACE_OF_SUPPLY, line.placeOfSupply());
    requiredText.put(Column.HSN_OR_SAC, line.hsnOrSac());
    requiredText.put(Column.TAXABLE_VALUE, line.taxableValue().text());
    requiredText.put(Column.RATE, line.rate().text());
    requiredText.put(Column.LINE_TOTAL, line.lineTotal().text());
    for (Map.Entry<Column, String> entry : requiredText.entrySet()) {
      if (entry.getValue().isBlank()) {
        issues.add(
            Issue.of(
                line,
                CheckId.DATA_001,
                entry.getKey(),
                "",
                "'" + entry.getKey().key() + "' is empty but is required on every line.",
                "Fill in " + entry.getKey().key() + " for this line."));
      }
    }

    Map<Column, Amount> numbers = new LinkedHashMap<>();
    numbers.put(Column.QUANTITY, line.quantity());
    numbers.put(Column.UNIT_PRICE, line.unitPrice());
    numbers.put(Column.TAXABLE_VALUE, line.taxableValue());
    numbers.put(Column.RATE, line.rate());
    numbers.put(Column.CGST, line.cgst());
    numbers.put(Column.SGST, line.sgst());
    numbers.put(Column.IGST, line.igst());
    numbers.put(Column.CESS, line.cess());
    numbers.put(Column.LINE_TOTAL, line.lineTotal());
    for (Map.Entry<Column, Amount> entry : numbers.entrySet()) {
      if (entry.getValue().isInvalid()) {
        issues.add(
            Issue.of(
                line,
                CheckId.DATA_002,
                entry.getKey(),
                entry.getValue().text(),
                "'"
                    + entry.getValue().text()
                    + "' in "
                    + entry.getKey().key()
                    + " is not a plain decimal number.",
                "Use digits with an optional decimal point, such as 1250.50. Remove thousands"
                    + " separators, currency symbols, % signs and spreadsheet notation such as"
                    + " 1.2E+05."));
      }
    }
  }
}
