# PLAN — gst-invoice-checker

A Java 21 command-line tool that checks a CSV of invoice lines against structural Indian GST rules and writes an error report. Fully offline. A data-quality helper, not tax advice.

Status: **approved on 12 October 2026 and built.** The plan below is kept as written. What changed during the build is listed at the end.

## 1. Environment (inspected 2026-10-12)

| Tool | Found | Needed |
|---|---|---|
| JDK | **missing** | Java 21 (Temurin 21 is available through winget) |
| Maven | **missing** | 3.9.x, or none if we use the Maven Wrapper (decision A) |
| git | 2.49.0 | ok |
| Docker | missing | only to test the Dockerfile locally |
| Edge | present | used headless to take the real report screenshot |
| OS | Windows 11, x64 | CI will also run on Linux |

Coding cannot start until a JDK is installed. `brain.md` is written for Python projects, so I map it: `pytest` becomes `mvn verify`, pinned `requirements.txt` becomes pinned versions in `pom.xml`, and `.env.example` is dropped because the tool has no secrets and no network.

## 2. Decisions I need from you

| | Decision | My recommendation |
|---|---|---|
| A | Commit the Maven Wrapper (`mvnw`, `mvnw.cmd`) so only a JDK is needed | Yes. Faster for you now and for anyone who clones it. |
| B | Add an interactive screen (you have asked for one on earlier projects) | CLI only. Your spec says "no network access anywhere in the code", and a local web UI needs a listening socket. The HTML report is filterable but read-only. Say so if you want a UI anyway and I will plan a `ui` subcommand bound to 127.0.0.1. |
| C | What happens when `check` runs with no config | Look for `gst-checker.yml` in the current folder, otherwise exit 2 and point to `sample`. No silent built-in rate list. |
| D | Default rounding tolerance | `0.01` (one paisa), inclusive. Invoices that round tax to whole rupees will need a larger value in the config. |
| E | GitHub owner for badge and package name | `Omkar210404` (taken from your other repos), package `io.github.omkar210404.gstcheck` |

## 3. Checks

Every check has an ID, a default severity, a plain-English message and a suggested fix. Any check can be disabled or have its severity changed in the config.

| ID | Sev | Flags when |
|---|---|---|
| DATA-001 | error | A required value is empty |
| DATA-002 | error | A number cannot be parsed as a plain decimal (no thousands separators, no currency symbols) |
| GSTIN-001 | error | Not exactly 15 characters, or contains characters other than A–Z and 0–9 |
| GSTIN-002 | error | First two digits are not a state code in the config |
| GSTIN-003 | error | Characters 3–12 are not PAN-shaped (5 letters, 4 digits, 1 letter) |
| GSTIN-004 | error | Character 13 (entity number) is not 1–9 or A–Z |
| GSTIN-005 | error | Character 14 is not `Z` |
| GSTIN-006 | error | Character 15 does not match the mod-36 check character |
| GSTIN-007 | warning | Lower case or surrounding spaces; the value is then checked in upper-case trimmed form |
| INV-001 | error | Invoice number longer than 16 characters |
| INV-002 | error | Invoice number has characters other than letters, digits, `/` and `-` |
| INV-003 | error | Same supplier, number and financial year appears with a different date, buyer or place of supply |
| INV-004 | warning | Two invoice numbers of one supplier in one year differ only by letter case |
| DATE-001 | error | Not a real date in any configured format (strict: 30 February fails) |
| DATE-002 | error | Date is after the as-of date |
| DATE-003 | error | Date is outside the configured financial year |
| HSN-001 | error | HSN/SAC is not digits only |
| HSN-002 | error | HSN/SAC length is not in the allowed list |
| POS-001 | error | Place of supply is not a two-digit state code in the config |
| TAX-001 | error | Supplier state equals place of supply but the line is not CGST + SGST |
| TAX-002 | error | Supplier state differs from place of supply but the line is not IGST |
| TAX-003 | error | IGST and CGST/SGST are both non-zero on one line (suppresses TAX-001/002) |
| TAX-004 | error | CGST and SGST are not equal |
| MATH-001 | error | CGST + SGST + IGST differs from taxable value × rate by more than the tolerance |
| MATH-002 | error | Line total differs from taxable value + taxes + cess by more than the tolerance |
| MATH-003 | warning | Taxable value differs from quantity × unit price (only when both are present) |
| MATH-004 | error | Any amount, quantity or rate is negative |
| MATH-005 | warning | Taxable value, quantity or line total is zero |
| RATE-001 | error | Rate is not in the configured slab list |

GSTIN checks run on `supplier_gstin` (required) and `buyer_gstin` (empty is allowed and means an unregistered buyer). The issue names the field. When a value cannot be parsed, DATA-00x is reported once and the dependent checks skip that line instead of piling on.

## 4. CSV schema

One row per invoice line. Extra columns are ignored. UTF-8, with or without a BOM.

| Column | Required | Type | Notes |
|---|---|---|---|
| `invoice_no` | yes | text | |
| `invoice_date` | yes | date | Formats set in config |
| `supplier_gstin` | yes | text | |
| `buyer_gstin` | no | text | Empty = unregistered buyer |
| `place_of_supply_state_code` | yes | text | Two digits, leading zero kept (`07`) |
| `hsn_or_sac` | yes | text | Read as text so leading zeros survive |
| `description` | no | text | |
| `quantity`, `unit`, `unit_price` | no | decimal, text, decimal | Often absent for services |
| `taxable_value` | yes | decimal | |
| `rate` | yes | decimal | Percent, e.g. `18` or `0.25` |
| `cgst`, `sgst`, `igst`, `cess` | no | decimal | Blank means 0. UTGST goes in `sgst`. |
| `line_total` | yes | decimal | |

A required column missing from the header is an input problem (exit 2), not an issue row. The sample also carries optional `supplier_name` and `buyer_name` columns holding the fictional names; they are shown in the drill-down when present.

## 5. Config (`gst-checker.yml`)

```yaml
financial_year: "2026-27"            # 1 April 2026 to 31 March 2027
date_formats: ["yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy"]

hsn:
  allowed_lengths: [4, 6, 8]

rates:
  # EXAMPLE ONLY. GST rates change. Verify every value against the official
  # GST rate notifications before relying on this list.
  allowed_slabs: [0, 0.25, 3, 5, 12, 18, 28, 40]

tolerance:                           # absolute rupees, inclusive
  tax_amount: "0.01"
  line_total: "0.01"
  quantity_price: "0.01"

states:                              # EXAMPLE list, verify before use
  "27": { name: "Maharashtra", union_territory: false }
  "04": { name: "Chandigarh",  union_territory: true }
  # ...

# Codes to treat as the same territory when deciding intra vs inter-state.
# Empty by default. The tool does not guess mergers or renumbering.
equivalent_state_codes: []           # e.g. [["25", "26"]]

columns:                             # canonical name: header in your file
  invoice_no: "Bill No"

checks:
  disabled: []                       # e.g. ["MATH-003"]
  severity: {}                       # e.g. { "MATH-005": "error" }
```

`union_territory` only changes the wording of messages (CGST + UTGST instead of CGST + SGST). The intra/inter decision is always "supplier state code equals place of supply code", after applying `equivalent_state_codes`. Unknown keys and bad values fail fast with exit 2.

## 6. CLI

```
java -jar gst-invoice-checker.jar check <invoices.csv> [--config FILE] [--out-dir reports]
                                        [--as-of YYYY-MM-DD] [--strict]
java -jar gst-invoice-checker.jar gstin <GSTIN> [--config FILE]
java -jar gst-invoice-checker.jar sample [--dir sample] [--seed N]
```

- Exit codes: `0` no errors (warnings allowed unless `--strict`), `1` errors found, `2` usage or input problem.
- `--as-of` fixes "today" for DATE-002 so runs and golden tests are reproducible. Default is the system date.
- `gstin` explains each part of the number and always prints that a pass is structural only.
- `sample` is deterministic for a given seed, so README output can be reproduced.

## 7. Package layout

```
io.github.omkar210404.gstcheck
  cli      Main, CheckCommand, GstinCommand, SampleCommand, ExitCodes
  config   CheckerConfig (records), ConfigLoader, FinancialYear
  csvio    InvoiceCsvReader, ColumnMapping
  gstin    GstinValidator, GstinChecksum, GstinResult, SyntheticGstin
  model    InvoiceLine, Issue, Severity, CheckId, CheckRun
  rules    Rule, RuleContext, RuleEngine, one class per rule
  report   ConsoleReport, IssuesCsvReport, JsonReport, HtmlReport
  sample   SampleDataGenerator
```

- `Rule` has `definitions()` (the check IDs it owns) and `apply(batch, context)`. Rules never throw on bad data and never do I/O.
- Rule classes: `GstinRule`, `InvoiceNumberRule`, `DuplicateInvoiceRule`, `InvoiceDateRule`, `HsnSacRule`, `PlaceOfSupplyRule`, `TaxTypeRule`, `TaxAmountRule`, `LineTotalRule`, `QuantityPriceRule`, `AmountSignRule`, `RateSlabRule`.
- `CheckId` is the single catalogue of ID, default severity and title. A test fails if the README table drifts from it.
- All model types are records. All money is `BigDecimal` built from the CSV text, compared with `compareTo`, never `double`.
- "Today" comes from an injected `Clock`.

## 8. Dependencies

Versions are pinned in `pom.xml` to the latest stable release found on Maven Central at build time.

| Dependency | Why |
|---|---|
| picocli | Subcommands, help text, usage errors already exit 2 |
| Apache Commons CSV | Correct quoting, BOM and embedded newlines |
| Jackson databind + YAML | JSON report and YAML config with one library |
| JUnit 5, jqwik (test only) | Unit tests and property tests |
| maven-shade-plugin | Runnable fat JAR |
| Spotless + google-java-format | Formatting enforced, build fails if unformatted |
| SpotBugs | Static analysis |

SpotBugs over Checkstyle: Spotless already owns layout, so Checkstyle would mostly repeat it, while SpotBugs reads bytecode and finds real defects (null dereferences, ignored return values, exposed mutable state). No template engine: the HTML report is one resource file with a handful of placeholders.

## 9. Reports

- **Console**: counts of lines, invoices, errors and warnings, a per-check table, and the first issues. Plain ASCII, no colour library.
- **`issues.csv`**: `line_no, invoice_no, supplier_gstin, check_id, severity, field, value, message, suggested_fix`.
- **`report.json`**: tool version, as-of date, input file name, config summary, counts, issues, per-invoice roll-up, disclaimer.
- **`report.html`**: one self-contained file. Inline CSS and plain JavaScript, no CDN, no fonts, no network. Dark theme. Count tiles, filters by severity and check ID, text search, and an expandable row per invoice showing its lines and issues.
- CSV content is untrusted. The HTML embeds data as escaped JSON and renders with `textContent`, so a cell containing markup cannot run.
- The JSON, HTML footer, console output and `gstin` output all carry: a GSTIN that passes is structurally valid only and may not be registered or active; this tool is not tax advice and does not replace the GST portal, an accountant or e-invoice validation.

## 10. Sample data

- Fictional names that say so (for example "Kaveri Synthetic Traders (FICTIONAL)").
- GSTINs are generated with valid check characters and labelled synthetic. The README states that a generated number could match a real one by coincidence and must never be used as an identity.
- The file mixes clean invoices with one seeded fault per check, described in the `description` column, so a run on the sample shows every check firing.

## 11. Tests

- **Per rule**: one test class each, with the edge cases you listed: bad check character, unknown state code, 16 versus 17 character invoice numbers, difference exactly at tolerance versus one paisa over, intra versus inter-state, mixed tax, UT wording, equivalent state codes.
- **GSTIN**: a hand-worked checksum example written out in the test, a round-trip test (generate then validate), and single-character mutation tests (any one changed character must fail).
- **Property (jqwik)**: the validator never throws for arbitrary strings, including empty, very long, Unicode and control characters, and always returns a result.
- **Golden files**: a fixed input and config produce `issues.csv`, `report.json`, `report.html` and console text that must match checked-in files. Line endings are normalised. I review each golden file by hand when it is first created.
- **CLI**: exit codes 0, 1 and 2 tested through picocli without spawning a JVM.
- **No-network guard**: a test fails if main sources reference `java.net`, `HttpClient` or sockets.
- **Sample**: generated GSTINs all pass the validator, and the sample run produces exactly the expected set of check IDs.

Only numbers from real runs go in the README: test counts from Surefire, timings from actual commands.

## 12. CI and release

- `ci.yml`: on every push and pull request, `setup-java` (Temurin 21, Maven cache), `mvn -B verify` on Ubuntu and Windows. That runs Spotless check, tests and SpotBugs. A second job builds the Docker image and runs `gstin` as a smoke test.
- `release.yml`: on a `v*` tag, checks the tag matches the `pom.xml` version, builds, and attaches the fat JAR to a GitHub release with the preinstalled `gh` CLI.
- CI badge in the README pointing at `Omkar210404/gst-invoice-checker`.
- First release `v0.1.0`. I will not create the GitHub repo or push; that stays with you.

## 13. Build order

Each step ends with a green `mvn verify` and its own commit or commits.

1. Scaffold: `pom.xml`, wrapper, Spotless, SpotBugs, CI workflow, LICENSE
2. `gstin` package with unit and property tests
3. `config` and `csvio`
4. `model`, rule engine, then one commit per rule
5. Reports and golden files
6. CLI commands and exit-code tests
7. Sample generator
8. Fat JAR, Dockerfile, release workflow
9. Run the JAR on the sample, capture real output and a real screenshot, write the README
10. Definition-of-done pass and final report

## 14. Rules I am unsure about

These are the places where I would rather ask than guess.

1. **Checksum test vectors.** I cannot use real GSTINs, so correctness rests on a hand-worked example and mutation tests. If you have a published sample number from official GST documentation, I will add it.
2. **Character 14.** You specified `Z`. I have seen claims that some registration types use another letter but cannot confirm it offline. GSTIN-005 stays an error by default and can be downgraded in the config.
3. **Character 13.** I plan to accept 1–9 and A–Z and reject `0`. I am not certain `0` is never issued.
4. **Non-PAN registrations.** UIN, non-resident, TDS/TCS and OIDAR numbers have other shapes and will fail GSTIN-003. Listed as a limitation; not modelled.
5. **State code list.** Codes have been merged and added over time, and codes such as 96, 97 and 99 have special meanings. The list lives in the config as an example for you to verify.
6. **Duplicates.** The file is one row per line, so a repeated invoice number is normal. I treat it as a duplicate only when the repeated number has a different date, buyer or place of supply. Two genuinely separate invoices that share all three would not be caught.
7. **SEZ, exports, reverse charge.** These can legitimately be IGST within a state or carry no tax. The schema has no column for them, so TAX-001 can raise a false alarm. Listed as a limitation.
8. **Cess.** There is no cess rate column, so cess is only checked for sign and included in the line total.
9. **Synthetic PAN part.** I would like to use a fourth PAN letter outside the usual holder-type letters to lower the chance of matching a real number. I am not certain of the full letter list, so the README warning stays either way.
10. **Invoice number rules.** I enforce only what you listed: length and character set. No rule on leading zeros or starting with a special character.

## 15. Risks

| Risk | Handling |
|---|---|
| No JDK on this machine | Blocker for step 1. Install command is in the chat. |
| Workflows and Dockerfile cannot be run locally | Written carefully, marked unverified in the final report until the first push runs them. |
| Excel damages CSVs (stripped leading zeros, reformatted dates) | Fields read as text, messages name the likely cause, README has a section on it. |
| Whole file held in memory for duplicate detection | Fine for typical files. I will state the largest size I actually tested, nothing more. |
| A user reads "valid" as "registered" | The structural-only wording appears in every output, not just the README. |
| Example rates or state codes go stale | Config only, labelled example, verify comment, nothing in code or README prose. |
| Golden files hide a wrong answer | Small fixtures, reviewed by hand, with rule unit tests asserting the same cases independently. |

## 16. What changed during the build

- **Decisions A to E** were taken as recommended: Maven Wrapper committed, CLI only, no built-in config fallback for `check`, tolerance `0.01`, owner `Omkar210404`.
- **`union_territory` became `utgst`.** Some union territories charge SGST, so the flag now says what it controls: whether messages say UTGST instead of SGST.
- **A thirteenth rule, `DataQualityRule`,** owns DATA-001 and DATA-002. It was in the checks list but missing from the list of rule classes.
- **Console messages are wrapped,** not shortened, and `--max-issues` was added to control how many are listed.
- **`issues.csv` guards against spreadsheet formula injection.** Not in the plan; added because the file echoes values from the input.
- **google-java-format is pinned to 1.36.1.** Version 1.37.0 would not run on JDK 21 here.
- **Synthetic GSTINs use `X` as the fourth PAN letter** (unsure item 9). The README warning about coincidental matches stays.
- **Checksum vectors** (unsure item 1): a hand-worked example on the dummy body `27AAAAA0000A1Z`, exhaustive single-character mutation tests, and property tests. No real GSTIN is in the repository.
- **Decision B was reversed after the first demo.** A read-only report with no way to upload a file was not usable, so a `ui` command was added. It serves one page from a loopback-only server built on the HTTP server inside the JDK: no new dependency and no outbound connections. The "no network access" rule became "never contacts another machine", and the no-network test was rewritten to enforce exactly that.
- **Not verified locally:** the Dockerfile and both GitHub workflows. Docker is not installed on the build machine and the repository had not been pushed.
