package io.github.omkar210404.gstcheck.ui;

import java.awt.Desktop;
import java.net.URI;

/** Opens the local page in the user's default browser, when the platform allows it. */
public final class Browser {

  private Browser() {}

  /**
   * Asks the desktop to open an address. Returns false instead of failing when there is no desktop
   * (a server, a container) or the request is refused; the caller prints the address either way.
   */
  public static boolean open(String address) {
    try {
      if (!Desktop.isDesktopSupported()
          || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        return false;
      }
      Desktop.getDesktop().browse(URI.create(address));
      return true;
    } catch (Exception | LinkageError e) {
      return false;
    }
  }
}
