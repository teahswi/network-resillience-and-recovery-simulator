package netrescue.server;

import java.awt.Desktop;
import java.io.IOException;
import java.net.BindException;
import java.net.URI;

/**
 * Entry point.
 *
 *   java -jar netrescue.jar                 start on port 8080 and open the browser
 *   java -jar netrescue.jar --port 9000     use another port
 *   java -jar netrescue.jar --no-browser    don't open the browser automatically
 */
public final class App {
    private App() {}

    public static void main(String[] args) throws Exception {
        int port = 8080;
        boolean openBrowser = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--no-browser" -> openBrowser = false;
                case "--help", "-h" -> {
                    System.out.println("usage: java -jar netrescue.jar [--port N] [--no-browser]");
                    return;
                }
                default -> { System.err.println("unknown option " + args[i]); return; }
            }
        }
        Api api = new Api();
        WebServer server = null;
        for (int p = port; p < port + 10 && server == null; p++) {
            try { server = new WebServer(p, api); }
            catch (BindException e) { System.out.println("Port " + p + " is busy, trying " + (p + 1) + "..."); }
        }
        if (server == null) throw new IOException("no free port between " + port + " and " + (port + 9));
        server.start();
        String url = "http://localhost:" + server.port() + "/";
        System.out.println("NetRescue is running at " + url);
        System.out.println("Press Ctrl+C to stop.");
        if (openBrowser) {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                    Desktop.getDesktop().browse(new URI(url));
            } catch (Exception | Error e) {
                System.out.println("(Open " + url + " in your browser.)");
            }
        }
    }
}
