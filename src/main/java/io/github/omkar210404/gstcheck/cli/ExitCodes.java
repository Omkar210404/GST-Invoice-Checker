package io.github.omkar210404.gstcheck.cli;

/** Process exit codes, chosen so the tool is easy to use from scripts and CI. */
public final class ExitCodes {

  /** No errors found (warnings are allowed unless {@code --strict} is given). */
  public static final int OK = 0;

  /** The check ran and found errors. */
  public static final int ERRORS_FOUND = 1;

  /** The tool could not run: bad arguments, unreadable input or an invalid config. */
  public static final int USAGE = 2;

  private ExitCodes() {}
}
