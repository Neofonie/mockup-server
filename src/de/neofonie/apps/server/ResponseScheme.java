package de.neofonie.apps.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to handle and manage response to be send by this mockup server.
 * 
 * @author kai.uecker@neofonie.de
 */
public class ResponseScheme {

    public static final int //
            UNKNOWN = 0,
            JSON = 1,
            HTML = 2,
            FILE = 3;
    private static final int //
            IDLE = 0,
            START_BLOCK = 1,
            URL_IS_SET = 2,
            RESPONSE_IS_SET = 3;

    private File file;
    private int state;
    private List<Response> responses;
    private long lastTimeStamp;

    public ResponseScheme(File file) {
        this.file = file;
        this.state = IDLE;
        this.responses = new ArrayList<>(16);
        this.lastTimeStamp = file.lastModified();

        this.read();
        this.initFileChangeListener();
    }

    public Response fetchResponseFor(String url) {
        if (url != null) {
            for (Response response : this.responses) {
                if (response.matches(url)) {
                    return response;
                }
            }
        }
        return null;
    }

    private void initFileChangeListener() {
        final Path dir = this.file.getParentFile().toPath();

        new Thread(() -> {
            try {
                WatchService watcher = FileSystems.getDefault().newWatchService();

                dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);

                while (true) {
                    WatchKey key;
                    try {
                        key = watcher.take();
                    } catch (InterruptedException ex) {
                        return;
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path fileName = ev.context();

                        long timeStamp = this.file.lastModified();

                        if (kind == ENTRY_MODIFY
                                && fileName.equals(file.toPath())
                                && this.lastTimeStamp != timeStamp) {
                            System.out.println("\tScheme has changed, re-read...");
                            this.lastTimeStamp = timeStamp;
                            this.read();
                        }
                    }

                    boolean valid = key.reset();
                    if (!valid) {
                        break;
                    }
                }

            } catch (IOException ex) {
                System.err.println(ex);
            }
        }).start();
    }

    private void read() {
        if (this.file.exists() && !this.file.isDirectory()) {
            System.out.println("\tReading response scheme '" + this.file.getName() + "'...");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(this.file), "UTF-8"))) {
                String //
                        line,
                        url = null;
                int type = UNKNOWN;
                StringBuilder response = new StringBuilder(1024);

                this.state = IDLE;
                this.responses.clear();

                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    if (this.state == IDLE && "{{FILE".equalsIgnoreCase(line)) {
                        this.state = START_BLOCK;

                        type = FILE;
                    } else if (this.state == IDLE && "{{JSON".equalsIgnoreCase(line)) {
                        this.state = START_BLOCK;

                        type = JSON;
                    } else if (this.state == IDLE && "{{HTML".equalsIgnoreCase(line)) {
                        this.state = START_BLOCK;

                        type = HTML;
                    } else if ("}}".equals(line)) {
                        if (this.state == RESPONSE_IS_SET || this.state == URL_IS_SET) {
                            System.out.println("\t\tresponse for url '" + url + "'");

                            this.responses.add(new Response(
                                    type,
                                    url,
                                    response.toString()
                            ));
                        }
                        url = null;
                        response.setLength(0);

                        this.state = IDLE;
                    } else {
                        if (line.length() > 0) {
                            if (this.state == START_BLOCK) {
                                url = line;
                                this.state = URL_IS_SET;
                            } else if (this.state == URL_IS_SET
                                    || this.state == RESPONSE_IS_SET) {
                                response.append(response.length() > 0 ? "\n" : "").append(line);
                                this.state = RESPONSE_IS_SET;
                            }
                        }
                    }
                }

            } catch (IOException ex) {
                Logger.getLogger(ResponseScheme.class.getName()).log(Level.SEVERE, null, ex);
            }
            System.out.println("\t... finished reading response scheme\n\n\n");
        }
    }

    public class Response {

        private int type;
        private String urlPattern, content;
        private Pattern pattern;
        private String[] urlParams;
        private File file;

        public Response(int type, String urlPattern, String content) {
            this.type = type;
            this.urlPattern = urlPattern;
            this.content = content;
            this.pattern = Pattern.compile(urlPattern, Pattern.CASE_INSENSITIVE);

            if (this.type == ResponseScheme.FILE) {
                this.file = new File(content);
            }
        }

        public boolean matches(String url) {
            if (url != null) {
                Matcher matcher = this.pattern.matcher(url);

                if (matcher.find()) {
                    this.urlParams = new String[matcher.groupCount() + 1];

                    for (int i = 0; i <= matcher.groupCount(); i++) {
                        this.urlParams[i] = matcher.group(i);
                    }

                    return true;
                } else {
                    this.urlParams = new String[0];
                }
            }

            return false;
        }

        public File getFile() {
            return this.file;
        }

        public String getUrl() {
            return this.urlPattern;
        }

        public int getType() {
            return this.type;
        }

        public String getContent() {
            return MessageFormat.format(this.content, this.urlParams);
        }
    }
}
