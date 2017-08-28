package de.neofonie.apps.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import de.neofonie.apps.server.ResponseScheme.Response;

/**
 * {@code MockupServer} can be used to simulate responses in form of HTML, JSON
 * or files. All responses are defined by a scheme-file including a definition
 * for the url where each response should be send.
 *
 * @author kai.uecker@neofonie.de
 */
public class MockupServer {

    private static final int //
            ERR_INVALID_PORT = -1,
            ERR_INVALID_SCHEME_NOT_EXISTING = -2,
            ERR_INVALID_SCHEME_WRONG_TYPE = -3,
            ERR_GENERAL = -999;
    private static final int //
            STATE_PORT = 1,
            STATE_SCHEME = 2;

    public static void main(String[] args) {

        new Thread(() -> {
            new MockupServer(args);
        }).start();
    }

    private static final String VERSION = "1.1.2";

    private ResponseScheme responeScheme;
    private int port;
    private File schemeFilePath;

    private MockupServer(String[] args) {
        this.port = 9090;
        this.schemeFilePath = new File("response.scheme").getAbsoluteFile();

        this.parseArguments(args);

        System.out.println("\n\n");
        System.out.println("Init Mockup Server...\n\n");
        this.responeScheme = new ResponseScheme(this.schemeFilePath);

        try (ServerSocket ss = new ServerSocket(this.port)) {
            try {
                System.out.println("Started Mockup Server @ port " + port + "\n\n");

                while (true) {
                    //Waiting for socket
                    Socket s = ss.accept();
                    //The main process
                    new SocketProcessor(s, ss).start();
                }
            } catch (IOException ex) {
                Logger.getLogger(MockupServer.class.getName()).log(Level.SEVERE, null, ex);
                System.exit(ERR_GENERAL);
            } catch (Throwable ex) {
                Logger.getLogger(MockupServer.class.getName()).log(Level.SEVERE, null, ex);
                System.exit(ERR_GENERAL);
            }
        } catch (IOException ex) {
            Logger.getLogger(MockupServer.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(ERR_GENERAL);
        }
    }

    private void parseArguments(String[] args) {
        if (args != null) {
            int state = 0;

            for (String arg : args) {
                switch (arg.trim().toLowerCase()) {
                    case "-h":
                    case "--help":
                        System.out.println("\nMockup Server\n\n"
                                + "-h, --help\tShows this help\n"
                                + "-p, --port\tDefines the port this server should use\n\t\t(default is 9090)\n"
                                + "-s, --scheme\tDefines the scheme-file used for this server\n\t\t(default is 'response.scheme')\n"
                                + "\n\n");
                        System.exit(0);
                        break;
                    case "-p":
                    case "--port":
                        state = STATE_PORT;
                        break;
                    case "-s":
                    case "--scheme":
                        state = STATE_SCHEME;
                        break;
                    default:
                        switch (state) {
                            case STATE_PORT: {
                                try {
                                    this.port = Integer.parseInt(arg);
                                    if (this.port < 0 || this.port > 65535) {
                                        throw new NumberFormatException();
                                    }
                                } catch (NumberFormatException ex) {
                                    System.err.println("Invalid value for 'port', must be a number and between 0 - 65535 but was '" + arg + "'");
                                    System.exit(ERR_INVALID_PORT);
                                }
                            }
                            break;
                            case STATE_SCHEME: {
                                File schemeFile = new File(arg);

                                if (!schemeFile.exists()) {
                                    System.err.println("Invalid value for 'scheme', '" + arg + "' doesn't exists as file");
                                    System.exit(ERR_INVALID_SCHEME_NOT_EXISTING);
                                } else if (schemeFile.isDirectory()) {
                                    System.err.println("Invalid value for 'scheme', '" + arg + "' is a directory but has to be a file");
                                    System.exit(ERR_INVALID_SCHEME_WRONG_TYPE);
                                } else {
                                    this.schemeFilePath = schemeFile.getAbsoluteFile();
                                }
                            }
                            break;
                        }
                        state = 0;
                        break;
                }
            }
        }
    }

    private class SocketProcessor implements Runnable {

        private Thread thread;
        private Socket socket;
        private InputStream in;
        private OutputStream out;
        private Map<String, String> header;

        private SocketProcessor(Socket s, ServerSocket ss) throws Throwable {
            this.header = new HashMap<>();
            this.thread = new Thread(this, "Server Thread");
            this.socket = s;
            this.in = s.getInputStream();
            this.out = s.getOutputStream();
        }

        public void run() {
            try {
                this.readRequestHeader();
                System.out.println("\tClient requested '" + this.header.get("get") + "'...\n");
                Response response = this.getResponse();

                if (response != null) {
                    switch (response.getType()) {
                        case ResponseScheme.HTML:
                            writeHTMLResponse(response.getContent());
                            break;
                        case ResponseScheme.JSON:
                            writeJSONResponse(response.getContent());
                            break;
                        case ResponseScheme.FILE:
                            writeBinaryResponse(response.getFile());
                            break;
                    }
                }
            } catch (Throwable t) {
            } finally {
                try {
                    this.socket.close();
                } catch (Throwable t) {

                }
            }
            System.out.println("\t...Client request finished\n\n\n");
        }

        public void start() {
            this.thread.start();
        }

        private void writeJSONResponse(String content) throws Throwable {
            this.writeResponse(createResponseHeader("application/json", this.getContentLength(content)) + content);
        }

        private void writeHTMLResponse(String content) throws Throwable {
            this.writeResponse(createResponseHeader("text/html", this.getContentLength(content)) + content);
        }

        private void writeBinaryResponse(File file) throws Throwable {
            this.writeFileResponse(createResponseHeader(this.getMimeType(file), this.getContentLength(file)), file);
        }

        private void writeResponse(String response) throws Throwable {
            System.out.println("\tresponding with:\n");
            System.out.println(response + "\n");

            this.out.write(response.getBytes());
            this.out.flush();
        }

        private void writeFileResponse(String response, File file) throws Throwable {
            System.out.println("\tresponding with:\n");
            System.out.println(response);

            this.out.write(response.getBytes());
            this.out.flush();
            if (file.exists() && !file.isDirectory()) {
                System.out.println("\tstreaming file '" + file.getAbsolutePath() + "'\n");

                try (FileInputStream fin = new FileInputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int len = fin.read(buffer);

                    while (len != -1) {
                        this.out.write(buffer, 0, len);
                        this.out.flush();
                        len = fin.read(buffer);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }

        private String createResponseHeader(String mimeType, long length) {
            return "HTTP/1.1 200 OK\r\n"
                    + "Server: " + MockupServer.class.getSimpleName() + " " + VERSION + "\r\n"
                    + "Content-Type: " + mimeType + "\r\n"
                    + "Content-Length: " + length + "\r\n"
                    + "Connection: close\r\n\r\n";
        }

        private long getContentLength(String content) {
            return content.length();
        }

        private long getContentLength(File file) {
            return file.length();
        }

        private String getMimeType(File file) {
            String name = file.getName().toLowerCase();

            if (name.endsWith(".css")) {
                return "text/css";
            } else if (name.endsWith(".htm")
                    || name.endsWith(".html")) {
                return "text/html";
            } else if (name.endsWith(".js")) {
                return "text/javascript";
            } else if (name.endsWith(".jpg")
                    || name.endsWith(".jpe")
                    || name.endsWith(".jpeg")) {
                return "image/jpeg";
            } else if (name.endsWith(".gif")) {
                return "image/gif";
            } else if (name.endsWith(".png")) {
                return "image/png";
            }

            return "application/octet-stream";
        }

        private void readRequestHeader() throws Throwable {
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line;

            this.header.clear();

            while ((line = br.readLine()) != null) {
                int i = line.indexOf(':');

                if (line.toLowerCase().startsWith("get ")) {
                    this.header.put("get", line.substring(4, line.length()).trim());
                } else if (i != -1) {
                    this.header.put(
                            line.substring(0, i).trim().toLowerCase(),
                            line.substring(i + 1, line.length()).trim());
                }
                if (line.trim().length() == 0) {
                    break;
                }
            }
        }

        private Response getResponse() {
            String url = this.header.get("get");

            if (url != null && url.trim().length() > 0) {
                return responeScheme.fetchResponseFor(url);
            }

            return null;
        }
    }
}
