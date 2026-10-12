package io.github.omkar210404.gstcheck.report;

import io.github.omkar210404.gstcheck.gstin.GstinResult;

/** The caveats that every output carries, kept in one place so the wording stays identical. */
public final class Notices {

  public static final String TOOL_NAME = "gst-invoice-checker";

  /** A GSTIN that passes is structurally valid only. */
  public static final String STRUCTURAL_ONLY = GstinResult.STRUCTURAL_ONLY_NOTICE;

  /** Not tax advice. */
  public static final String DISCLAIMER =
      "This is an offline data-quality helper, not tax advice. It does not replace the GST"
          + " portal, an accountant or e-invoice validation. A clean report means these"
          + " structural checks passed; it does not mean an invoice is legally compliant.";

  private Notices() {}
}
