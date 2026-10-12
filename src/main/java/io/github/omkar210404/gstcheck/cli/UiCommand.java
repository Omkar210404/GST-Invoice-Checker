package io.github.omkar210404.gstcheck.cli;

import io.github.omkar210404.gstcheck.model.InputProblemException;
import io.github.omkar210404.gstcheck.ui.Browser;
import io.github.omkar210404.gstcheck.ui.LocalServer;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** {@code ui}: serves a local page where a CSV can be picked and checked in the browser. */
@Command(
    name = "ui",
    mixinStandardHelpOptions = true,
    description = {
      "Open a local page to upload a CSV and see the report in your browser.",
      "The page is served from this computer on 127.0.0.1 only. Nothing is sent anywhere and",
      "uploads are kept in memory, not written to disk."
    })
final class UiCommand implements Callable<Integer> {

  @ParentCommand private Main parent;

  @Spec private CommandSpec spec;

  @Option(
      names = {"-p", "--port"},
      paramLabel = "PORT",
      defaultValue = "" + LocalServer.DEFAULT_PORT,
      description = "Port to listen on (0 picks a free one). Default: ${DEFAULT-VALUE}.")
  private int port;

  @Option(names = "--no-open", description = "Do not open the browser automatically.")
  private boolean noOpen;

  @Override
  public Integer call() throws InterruptedException {
    PrintWriter out = spec.commandLine().getOut();
    if (port < 0 || port > 65535) {
      throw new InputProblemException("--port must be between 0 and 65535.");
    }
    LocalServer server;
    try {
      server = LocalServer.start(port, parent.clock(), Main.version());
    } catch (IOException e) {
      throw new InputProblemException(
          "Could not open port "
              + port
              + " on this computer ("
              + e.getMessage()
              + "). It may already be in use: try another one with --port.",
          e);
    }
    Runtime.getRuntime().addShutdownHook(new Thread(server::close, "ui-shutdown"));

    out.println("gst-invoice-checker is running at:");
    out.println();
    out.println("  " + server.url());
    out.println();
    out.println("Open that address in a browser on this computer. It is not reachable from any");
    out.println("other machine, and nothing you upload leaves this computer or is saved to disk.");
    out.println("Press Ctrl+C to stop.");
    out.flush();
    if (!noOpen) {
      Browser.open(server.url());
    }
    server.awaitStop();
    return ExitCodes.OK;
  }
}
