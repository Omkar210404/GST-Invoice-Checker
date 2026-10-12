package io.github.omkar210404.gstcheck.model;

/**
 * Something is wrong with what the user gave the tool (a bad config, an unreadable CSV, a missing
 * column) as opposed to something wrong inside an invoice. The CLI turns this into exit code 2.
 */
public class InputProblemException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public InputProblemException(String message) {
    super(message);
  }

  public InputProblemException(String message, Throwable cause) {
    super(message, cause);
  }
}
