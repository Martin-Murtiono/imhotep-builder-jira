package com.indeed.jiraactions.jiraissues;

import com.indeed.jiraactions.JiraActionsIndexBuilderConfig;
import org.apache.commons.codec.binary.Base64;
import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class JiraIssuesFileWriter {
    private static final Logger log = LoggerFactory.getLogger(JiraIssuesIndexBuilder.class);

    private final JiraActionsIndexBuilderConfig config;

    private WriterData writerData;
    private List<String[]> newIssues = new ArrayList<>();
    private TsvParserSettings settings = new TsvParserSettings();
    private String[] headers;

    public JiraIssuesFileWriter(JiraActionsIndexBuilderConfig config) {
        this.config = config;
        this.settings = setupSettings(this.settings);
    }

    public TsvParserSettings setupSettings(TsvParserSettings settings) {
        settings.getFormat().setLineSeparator("\n");
        settings.setMaxColumns(1000);
        settings.setMaxCharsPerColumn(10000);
        settings.setNullValue("");
        return settings;
    }


    public void downloadTsv(DateTime date) throws IOException {
        String formattedDate = date.minusDays(1).toString("yyyyMMdd");

        final String userPass = config.getIuploadUsername() + ":" + config.getIuploadPassword();
        final String basicAuth = "Basic " + new String(new Base64().encode(userPass.getBytes()));

        File file = new File("jiraissues_downloaded.tsv");
        file.deleteOnExit();
        FileOutputStream stream = new FileOutputStream(file);

        for(int i = 0; i <= NUM_RETRIES; i++) {
            try {
                URL url = new URL("https://squall.indeed.com/iupload/repository/qa/index/jiraissues/file/indexed/jiraissues_" + formattedDate + ".tsv/");
                if (i == 5) {
                    url = new URL("https://squall.indeed.com/iupload/repository/qa/index/jiraissues/file/indexing/jiraissues_" + formattedDate + ".tsv/");
                }
                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestProperty("Authorization", basicAuth);

                final BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                int length;
                byte[] buffer = new byte[1024];
                while ((length = in.read(buffer)) > -1) {
                    stream.write(buffer, 0, length);
                }
                stream.close();
                in.close();
                break;
            } catch (final IOException e) {
                log.error("Unable to download file.", e);
            }
        }
    }

    private static final int NUM_RETRIES = 5;
    public void uploadTsv() {
        final String iuploadUrl = String.format("https://squall.indeed.com/iupload/repository/qa/index/jiraissues/file/");

        log.info("Uploading to " + iuploadUrl);

        final String userPass = config.getIuploadUsername() + ":" + config.getIuploadPassword();
        final String basicAuth = "Basic " + new String(new Base64().encode(userPass.getBytes()));
        try {
            writerData.getBufferedWriter().close();
        } catch (final IOException e) {
            log.error("Failed to close" + writerData.file.getName() + ".", e);
        }

        final File file = writerData.getFile();
        if (writerData.isWritten()) {
            final HttpPost httpPost = new HttpPost(iuploadUrl);
            httpPost.setHeader("Authorization", basicAuth);
            httpPost.setEntity(MultipartEntityBuilder.create()
                    .addBinaryBody("file", file, ContentType.MULTIPART_FORM_DATA, file.getName())
                    .build());

            for(int i = 0; i < NUM_RETRIES; i++) {
                try {
                    final HttpResponse response = HttpClientBuilder.create().build().execute(httpPost);
                    log.info("Http response: " + response.getStatusLine().toString() + ": " + file.getName() + ".");
                    if(response.getStatusLine().getStatusCode() != 200) {
                        continue;
                    }
                    return;
                } catch (final IOException e) {
                    log.warn("Failed to upload file: " + file.getName() + ".", e);
                }
            }
            log.error("Retries expired, unable to upload file: " +file.getName() + ".");
        }
    }

    public void parseNewTsv() throws Exception {
        final File file = new File("jiraissues_temp.tsv");
        FileReader reader = new FileReader(file);
        TsvParser parser = new TsvParser(settings);

        this.newIssues = parser.parseAll(reader);
        headers = newIssues.get(0);
        newIssues.remove(0);
    }

    public void process() throws Exception {
        File file = new File("jiraissues_downloaded.tsv");
        FileReader reader = new FileReader(file);
        TsvParser parser = new TsvParser(settings);

        parser.beginParsing(reader);
        String[] a = parser.parseNext(); // skip headers


        /** If the issue is updated through jiraactions it will replace it because that version is the latest.
        * If it isn't replaced then it gets updated -- only fields involving time are updated so this is really easy.
         * Issues from jiraactions are removed when they get replaced meaning that the ones remaining are new issues and are therefore added. */
        int updateCount = 0;
        int replaceCount = 0;
        while (true) {
            boolean replaced = false;
            String[] row = parser.parseNext();
            if (row == null) {
                break;
            } else {
                for (String[] issue : newIssues) {
                    if (row[0].equals(issue[0])) {
                        replaceCount++;
                        writeIssue(issue);  // Replace
                        replaced = true;
                        newIssues.remove(issue);
                        break;
                    }
                }
                if (!replaced) {
                    updateCount++;
                    writeIssue(updateIssue(row));   // Update
                }
            }
        }
        log.debug("Updated {}, Replaced {}, issues.", updateCount, replaceCount);
        int added = 0;
        if (!newIssues.isEmpty()) {
            for (String[] issue : newIssues) {
                writeIssue(issue);  // Add new
                added++;
            }
            log.debug("Added {} new issues.", added);
            writerData.getBufferedWriter().close();
        }
    }

    public void createTsv(DateTime date) throws IOException {
        String formattedDate = date.toString("yyyyMMdd");
        final File file = new File("jiraissues_" + formattedDate + ".tsv");
        final BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        final String headerLine = String.join("\t", headers);
        bw.write(headerLine);
        bw.newLine();
        bw.flush();

        writerData = new WriterData(file, bw);
    }

    public void writeIssue(String[] issue) throws IOException {
        final String line = Arrays.stream(issue)
                .map(rawValue -> rawValue.replace("\t", "\\t"))
                .map(rawValue -> rawValue.replace("\n", "\\n"))
                .map(rawValue -> rawValue.replace("\r", "\\r"))
                .collect(Collectors.joining("\t"));
        final BufferedWriter bw = writerData.getBufferedWriter();
        writerData.setWritten();
        bw.write(line);
        bw.newLine();
        bw.flush();
        try {
            writerData.getBufferedWriter().flush();
        } catch (final IOException e) {
        }
    }

    public String[] updateIssue(String[] issue) {
        final long DAY = 86400;
        String status = "";
        for(int i = 0; i < headers.length; i++) {
            if(headers[i].equals("issueage") || headers[i].equals("time")) {
                issue[i] = String.valueOf(Long.parseLong(issue[i]) + DAY);
            }
            if(headers[i].equals("status")) {
                status = issue[i].toLowerCase()
                        .replace(" ", "_")
                        .replace("-", "_")
                        .replace("(", "")
                        .replace(")", "")
                        .replace("&", "and")
                        .replace("/", "_");
            }
            if(headers[i].contains(status) && !status.isEmpty()){
                if(headers[i].startsWith("totaltime")) {
                    issue[i] = String.valueOf(Long.parseLong(issue[i]) + DAY);
                }
            }
        }
        return issue;
    }

    private static class WriterData {
        private final File file;
        private final BufferedWriter bw;
        private boolean written = false;

        private WriterData(final File file, final BufferedWriter bw) {
            this.file = file;
            this.bw = bw;
        }

        private File getFile() {
            return file;
        }

        private BufferedWriter getBufferedWriter() {
            return bw;
        }

        private boolean isWritten() {
            return written;
        }

        private void setWritten() {
            this.written = true;
        }
    }
}
