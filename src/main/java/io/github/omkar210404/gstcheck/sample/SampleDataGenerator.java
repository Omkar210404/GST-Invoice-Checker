package io.github.omkar210404.gstcheck.sample;

import io.github.omkar210404.gstcheck.config.ConfigLoader;
import io.github.omkar210404.gstcheck.gstin.GstinChecksum;
import io.github.omkar210404.gstcheck.gstin.SyntheticGstin;
import io.github.omkar210404.gstcheck.model.Column;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * Writes SYNTHETIC sample data: ten clean invoices followed by one deliberately broken invoice for
 * each check, so a run on the sample shows every check firing.
 *
 * <p>Company names are fictional and say so. GSTINs are generated with valid check characters and
 * belong to nobody; one could still match a real registration by coincidence, so never use them as
 * a real identity. HSN codes and rates are placeholders, not classifications.
 */
public final class SampleDataGenerator {

  public static final long DEFAULT_SEED = 20260401L;

  /** Invoice numbers of the deliberately broken invoices start with this. */
  public static final String FAULT_PREFIX = "ERR/";

  private static final BigDecimal TWO = new BigDecimal("2");
  private static final String ZERO = "0.00";

  private SampleDataGenerator() {}

  /**
   * The generated files.
   *
   * @param invoicesCsv the sample invoice CSV
   * @param configYaml the example config
   * @param readme a short note saying the data is synthetic
   */
  public record Sample(String invoicesCsv, String configYaml, String readme) {}

  private record Party(String name, String gstin, String state) {}

  private record Item(String description, String hsn, String unit, String price, String rate) {}

  private static final Item WIDGET =
      new Item("Demo widget (synthetic item)", "12345678", "NOS", "1200.00", "18");
  private static final Item FASTENER =
      new Item("Sample fastener box (synthetic item)", "123456", "BOX", "450.00", "5");
  private static final Item SERVICE =
      new Item("Test consulting hours (synthetic item)", "998877", "HRS", "2000.00", "18");
  private static final Item GRAIN =
      new Item("Placeholder grain bag (synthetic item)", "1234", "BAG", "800.00", "0");
  private static final Item PREMIUM =
      new Item("Mock premium unit (synthetic item)", "87654321", "NOS", "5000.00", "28");

  /** One CSV row under construction. Fields are text, exactly as they will be written. */
  private static final class Row {
    String invoiceNo;
    String date;
    String supplierGstin;
    String buyerGstin;
    String pos;
    String hsn;
    String description;
    String quantity;
    String unit;
    String unitPrice;
    String taxable;
    String rate;
    String cgst;
    String sgst;
    String igst;
    String cess;
    String total;
    String supplierName;
    String buyerName;

    Row fault(String what) {
      this.description = "SYNTHETIC FAULT: " + what;
      return this;
    }

    /** For a correct row that only exists to set up the fault on the row after it. */
    Row setup(String what) {
      this.description = "Synthetic setup row: " + what;
      return this;
    }

    List<String> cells() {
      return Arrays.asList(
          invoiceNo,
          date,
          supplierGstin,
          buyerGstin,
          pos,
          hsn,
          description,
          quantity,
          unit,
          unitPrice,
          taxable,
          rate,
          cgst,
          sgst,
          igst,
          cess,
          total,
          supplierName,
          buyerName);
    }
  }

  public static Sample generate(long seed) {
    Random random = new Random(seed);
    Party kaveri = party(random, "Kaveri Synthetic Traders (FICTIONAL)", "29");
    Party sahyadri = party(random, "Sahyadri Sample Supplies (FICTIONAL)", "27");
    Party sukhna = party(random, "Sukhna Demo Stores (FICTIONAL)", "04");
    Party narmada = party(random, "Narmada Example Retail (FICTIONAL)", "24");
    Party deccan = party(random, "Deccan Test Industries (FICTIONAL)", "27");
    Party yamuna = party(random, "Yamuna Mock Enterprises (FICTIONAL)", "07");
    Party cauvery = party(random, "Cauvery Placeholder Foods (FICTIONAL)", "29");
    Party shivalik = party(random, "Shivalik Imaginary Hardware (FICTIONAL)", "04");
    Party walkIn = new Party("Unregistered buyer (FICTIONAL)", "", "");

    List<Row> rows = new ArrayList<>();

    // --- Clean invoices: none of these should raise an issue. ---
    rows.add(line("KS/26-27/0001", "2026-04-06", kaveri, cauvery, "29", WIDGET, 2));
    rows.add(line("KS/26-27/0001", "2026-04-06", kaveri, cauvery, "29", FASTENER, 10));
    rows.add(line("KS/26-27/0002", "2026-04-18", kaveri, deccan, "27", SERVICE, 8));
    rows.add(line("KS/26-27/0003", "2026-05-09", kaveri, walkIn, "29", GRAIN, 5));
    rows.add(line("KS/26-27/0003", "2026-05-09", kaveri, walkIn, "29", FASTENER, 2));
    rows.add(line("SS/26-27/0001", "2026-05-21", sahyadri, deccan, "27", PREMIUM, 1));
    rows.add(line("SS/26-27/0001", "2026-05-21", sahyadri, deccan, "27", WIDGET, 4));
    rows.add(line("SS/26-27/0001", "2026-05-21", sahyadri, deccan, "27", SERVICE, 3));
    rows.add(line("SS/26-27/0002", "2026-06-02", sahyadri, narmada, "24", WIDGET, 6));
    rows.add(line("SS/26-27/0003", "15/06/2026", sahyadri, yamuna, "07", FASTENER, 40));
    rows.add(line("SS/26-27/0003", "15/06/2026", sahyadri, yamuna, "07", GRAIN, 12));
    rows.add(line("SD/26-27/0001", "2026-07-07", sukhna, shivalik, "04", WIDGET, 1));
    rows.add(line("SD/26-27/0001", "2026-07-07", sukhna, shivalik, "04", SERVICE, 2));
    rows.add(line("SD/26-27/0002", "2026-08-19", sukhna, yamuna, "07", PREMIUM, 2));
    rows.add(line("SS/26-27/0004", "2026-09-03", sahyadri, walkIn, "27", FASTENER, 4));
    rows.add(line("KS/26-27/0004", "2026-09-28", kaveri, narmada, "24", SERVICE, 5));
    rows.add(line("KS/26-27/0004", "2026-09-28", kaveri, narmada, "24", WIDGET, 3));

    // --- Broken invoices: each one is built to trip one check. ---
    int n = 0;
    Row r;

    r = fault(++n, sahyadri, deccan).fault("HSN/SAC left empty (DATA-001)");
    r.hsn = "";
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("taxable value has a thousands separator (DATA-002)");
    r.taxable = "1,200.00";
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("buyer GSTIN has 14 characters (GSTIN-001)");
    r.buyerGstin = deccan.gstin().substring(0, 14);
    rows.add(r);

    r =
        fault(++n, sahyadri, deccan)
            .fault("buyer GSTIN state code 99 is not configured (GSTIN-002)");
    r.buyerGstin = withChecksum("99" + deccan.gstin().substring(2, 14));
    rows.add(r);

    r =
        fault(++n, sahyadri, deccan)
            .fault("buyer GSTIN PAN part has a digit for a letter (GSTIN-003)");
    r.buyerGstin = withChecksum("27AB1XD1234E1Z");
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("buyer GSTIN entity number is 0 (GSTIN-004)");
    r.buyerGstin = withChecksum(deccan.gstin().substring(0, 12) + "0Z");
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("buyer GSTIN character 14 is not Z (GSTIN-005)");
    r.buyerGstin = withChecksum(deccan.gstin().substring(0, 13) + "Y");
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("buyer GSTIN check character is wrong (GSTIN-006)");
    r.buyerGstin = deccan.gstin().substring(0, 14) + wrongCheckCharacter(deccan.gstin());
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("buyer GSTIN typed in lower case (GSTIN-007)");
    r.buyerGstin = deccan.gstin().toLowerCase(Locale.ROOT);
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("invoice number has 17 characters (INV-001)");
    r.invoiceNo = "ERR/2026-27/00010";
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("invoice number has _ and # (INV-002)");
    r.invoiceNo = "ERR_26-27#0011";
    rows.add(r);

    ++n;
    rows.add(fault(n, sahyadri, deccan).setup("first use of this invoice number"));
    r = fault(n, sahyadri, deccan).fault("same number reused with another date (INV-003)");
    r.date = "2026-07-25";
    rows.add(r);

    ++n;
    rows.add(fault(n, sahyadri, deccan).setup("first spelling of this invoice number"));
    r = fault(n, sahyadri, deccan).fault("same number in lower case (INV-004)");
    r.invoiceNo = r.invoiceNo.toLowerCase(Locale.ROOT);
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("30 February does not exist (DATE-001)");
    r.date = "30-02-2027";
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("date is in the future as of 2026-10-12 (DATE-002)");
    r.date = "2027-03-31";
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("date is the day before the financial year (DATE-003)");
    r.date = "2026-03-31";
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("HSN has a letter in it (HSN-001)");
    r.hsn = "84A1";
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("HSN has 5 digits (HSN-002)");
    r.hsn = "12345";
    rows.add(r);

    r = fault(++n, sahyadri, yamuna).fault("place of supply lost its leading zero (POS-001)");
    r.pos = "7";
    r.cgst = ZERO;
    r.sgst = ZERO;
    r.igst = "216.00";
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("IGST charged on an intra-state supply (TAX-001)");
    r.cgst = ZERO;
    r.sgst = ZERO;
    r.igst = "216.00";
    rows.add(r);

    r =
        fault(++n, sahyadri, narmada)
            .fault("CGST and SGST charged on an inter-state supply (TAX-002)");
    r.pos = "24";
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("IGST mixed with CGST and SGST (TAX-003)");
    r.cgst = "54.00";
    r.sgst = "54.00";
    r.igst = "108.00";
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("CGST and SGST are not equal halves (TAX-004)");
    r.cgst = "116.00";
    r.sgst = "100.00";
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("tax is 10.00 short of taxable value x rate (MATH-001)");
    r.cgst = "103.00";
    r.sgst = "103.00";
    r.total = "1406.00";
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("line total is 10.00 too high (MATH-002)");
    r.total = "1426.00";
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("taxable value is not quantity x unit price (MATH-003)");
    r.quantity = "2";
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("cess is negative (MATH-004)");
    r.cess = "-5.00";
    r.total = "1411.00";
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("taxable value and line total are zero (MATH-005)");
    r.quantity = "";
    r.unitPrice = "";
    r.taxable = ZERO;
    r.cgst = ZERO;
    r.sgst = ZERO;
    r.total = ZERO;
    rows.add(r);

    r = fault(++n, sahyadri, deccan).fault("rate 7 is not in the configured slab list (RATE-001)");
    r.rate = "7";
    r.cgst = "42.00";
    r.sgst = "42.00";
    r.total = "1284.00";
    rows.add(r);

    return new Sample(toCsv(rows), ConfigLoader.exampleText(), README);
  }

  private static Party party(Random random, String name, String state) {
    return new Party(name, SyntheticGstin.generate(random, state), state);
  }

  /** A correct line: amounts are computed exactly, with tax split in halves when intra-state. */
  private static Row line(
      String invoiceNo, String date, Party supplier, Party buyer, String pos, Item item, int qty) {
    BigDecimal taxable = new BigDecimal(item.price()).multiply(BigDecimal.valueOf(qty)).setScale(2);
    BigDecimal tax =
        taxable
            .multiply(new BigDecimal(item.rate()))
            .movePointLeft(2)
            .setScale(2, RoundingMode.HALF_UP);
    Row row = new Row();
    row.invoiceNo = invoiceNo;
    row.date = date;
    row.supplierGstin = supplier.gstin();
    row.supplierName = supplier.name();
    row.buyerGstin = buyer.gstin();
    row.buyerName = buyer.name();
    row.pos = pos;
    row.hsn = item.hsn();
    row.description = item.description();
    row.quantity = String.valueOf(qty);
    row.unit = item.unit();
    row.unitPrice = item.price();
    row.taxable = taxable.toPlainString();
    row.rate = item.rate();
    row.cess = ZERO;
    if (supplier.state().equals(pos)) {
      String half = tax.divide(TWO, 2, RoundingMode.HALF_UP).toPlainString();
      row.cgst = half;
      row.sgst = half;
      row.igst = ZERO;
    } else {
      row.cgst = ZERO;
      row.sgst = ZERO;
      row.igst = tax.toPlainString();
    }
    row.total =
        taxable
            .add(new BigDecimal(row.cgst))
            .add(new BigDecimal(row.sgst))
            .add(new BigDecimal(row.igst))
            .toPlainString();
    return row;
  }

  /** The starting point for a broken invoice: one correct intra-state widget line. */
  private static Row fault(int number, Party supplier, Party buyer) {
    String invoiceNo = String.format(Locale.ROOT, "%s26-27/%04d", FAULT_PREFIX, number);
    return line(invoiceNo, "2026-07-15", supplier, buyer, supplier.state(), WIDGET, 1);
  }

  private static String withChecksum(String body) {
    return body + GstinChecksum.compute(body);
  }

  private static char wrongCheckCharacter(String validGstin) {
    return validGstin.charAt(14) == 'A' ? 'B' : 'A';
  }

  private static String toCsv(List<Row> rows) {
    List<String> header = new ArrayList<>();
    for (Column column : Column.values()) {
      header.add(column.key());
    }
    CSVFormat format =
        CSVFormat.DEFAULT
            .builder()
            .setHeader(header.toArray(String[]::new))
            .setRecordSeparator("\n")
            .get();
    StringBuilder out = new StringBuilder();
    try (CSVPrinter printer = new CSVPrinter(out, format)) {
      for (Row row : rows) {
        printer.printRecord(row.cells());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toString();
  }

  private static final String README =
      """
      SYNTHETIC SAMPLE DATA - NOT REAL INVOICES

      sample-invoices.csv and gst-checker.yml were written by `gst-invoice-checker sample`.

      - Every company name is fictional.
      - Every GSTIN was generated by the tool with a valid check character so that it passes
        the structural checks. None was issued to anyone. A generated number could match a
        real registration by coincidence: never use one as a real identity.
      - HSN codes, prices and rates are placeholders, not tax classifications.
      - Invoice numbers starting with ERR/ are deliberately broken, one per check, so you can
        see what each check reports. The description column says what was broken.
      - The rate list and state list in gst-checker.yml are examples. Verify them against
        official GST notifications before checking real data.

      Try it:

        java -jar gst-invoice-checker.jar check sample-invoices.csv --config gst-checker.yml
      """;
}
