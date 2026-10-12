package io.github.omkar210404.gstcheck.rules;

import io.github.omkar210404.gstcheck.model.CheckId;
import io.github.omkar210404.gstcheck.model.Column;
import io.github.omkar210404.gstcheck.model.InvoiceLine;
import io.github.omkar210404.gstcheck.model.Issue;
import java.util.List;

/** POS-001: the place of supply must be one of the two-digit state codes in the config. */
public final class PlaceOfSupplyRule implements LineRule {

  @Override
  public List<CheckId> checks() {
    return List.of(CheckId.POS_001);
  }

  @Override
  public void check(InvoiceLine line, RuleContext context, List<Issue> issues) {
    String code = line.placeOfSupply();
    if (code.isEmpty() || context.config().isKnownState(code)) {
      return;
    }
    boolean droppedZero = code.length() == 1 && context.config().isKnownState("0" + code);
    issues.add(
        Issue.of(
            line,
            CheckId.POS_001,
            Column.PLACE_OF_SUPPLY,
            code,
            "Place of supply '" + code + "' is not a state code listed in the config.",
            droppedZero
                ? "Write the code with two digits: 0"
                    + code
                    + ". A spreadsheet may have dropped the leading zero."
                : "Use the two-digit GST state code. If the code is genuine, add it to 'states'"
                    + " in the config file."));
  }
}
