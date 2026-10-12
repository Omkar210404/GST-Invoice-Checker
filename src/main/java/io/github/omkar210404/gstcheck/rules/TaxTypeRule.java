package io.github.omkar210404.gstcheck.rules;

import io.github.omkar210404.gstcheck.config.CheckerConfig;
import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Column;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * TAX-001 to TAX-004: the kind of tax must match where the supply goes.
 *
 * <p>The supplier's state is the first two digits of the supplier GSTIN. When it is the same
 * territory as the place of supply the line must carry CGST and SGST in equal halves (UTGST goes in
 * the sgst column); otherwise it must carry IGST. "Same territory" is equal codes, or codes grouped
 * under {@code equivalent_state_codes} in the config.
 *
 * <p>The input has no column for SEZ supplies, exports or reverse charge, where other treatment can
 * be correct, so those lines can be flagged here wrongly.
 */
public final class TaxTypeRule implements LineRule {

  @Override
  public List<CheckId> checks() {
    return List.of(CheckId.TAX_001, CheckId.TAX_002, CheckId.TAX_003, CheckId.TAX_004);
  }

  @Override
  public void check(InvoiceLine line, RuleContext context, List<Issue> issues) {
    CheckerConfig config = context.config();
    Optional<String> supplierState = supplierState(line, config);
    String placeOfSupply = line.placeOfSupply();
    if (supplierState.isEmpty() || !config.isKnownState(placeOfSupply)) {
      return;
    }
    if (line.cgst().isInvalid() || line.sgst().isInvalid() || line.igst().isInvalid()) {
      return;
    }
    BigDecimal cgst = line.cgst().orZero();
    BigDecimal sgst = line.sgst().orZero();
    BigDecimal igst = line.igst().orZero();
    boolean hasIgst = igst.signum() != 0;
    boolean hasSplit = cgst.signum() != 0 || sgst.signum() != 0;
    if (!hasIgst && !hasSplit) {
      return;
    }

    String stateTax = config.chargesUtgst(placeOfSupply) ? "UTGST" : "SGST";
    String route =
        "supplier state "
            + config.describeState(supplierState.get())
            + ", place of supply "
            + config.describeState(placeOfSupply);
    String amounts =
        "cgst="
            + cgst.toPlainString()
            + ", sgst="
            + sgst.toPlainString()
            + ", igst="
            + igst.toPlainString();

    if (hasIgst && hasSplit) {
      issues.add(
          Issue.of(
              line,
              CheckId.TAX_003,
              Column.IGST,
              amounts,
              "The line has both IGST and CGST/" + stateTax + ". A line carries one or the other.",
              "Keep IGST for an inter-state supply or CGST + "
                  + stateTax
                  + " for an intra-state"
                  + " supply, and clear the other columns."));
      return;
    }

    boolean intraState = config.sameTerritory(supplierState.get(), placeOfSupply);
    if (intraState && hasIgst) {
      issues.add(
          Issue.of(
              line,
              CheckId.TAX_001,
              Column.IGST,
              amounts,
              "IGST is charged but the supply is intra-state (" + route + ").",
              "Charge CGST + "
                  + stateTax
                  + " in equal halves"
                  + (config.chargesUtgst(placeOfSupply) ? " (enter UTGST in the sgst column)" : "")
                  + ", or correct the place of supply. SEZ and export supplies are not modelled"
                  + " by this tool."));
    } else if (!intraState && hasSplit) {
      issues.add(
          Issue.of(
              line,
              CheckId.TAX_002,
              Column.CGST,
              amounts,
              "CGST/SGST is charged but the supply is inter-state (" + route + ").",
              "Charge IGST instead, or correct the place of supply."));
    } else if (intraState && cgst.compareTo(sgst) != 0) {
      issues.add(
          Issue.of(
              line,
              CheckId.TAX_004,
              Column.SGST,
              amounts,
              "CGST ("
                  + cgst.toPlainString()
                  + ") and "
                  + stateTax
                  + " ("
                  + sgst.toPlainString()
                  + ") should be equal halves of the tax.",
              "Split the tax equally between cgst and sgst."));
    }
  }

  /** The first two digits of the supplier GSTIN, when they are a configured state code. */
  private static Optional<String> supplierState(InvoiceLine line, CheckerConfig config) {
    String gstin = line.supplierGstinNormalized();
    if (gstin.length() < 2) {
      return Optional.empty();
    }
    String code = gstin.substring(0, 2);
    return config.isKnownState(code) ? Optional.of(code) : Optional.empty();
  }
}
