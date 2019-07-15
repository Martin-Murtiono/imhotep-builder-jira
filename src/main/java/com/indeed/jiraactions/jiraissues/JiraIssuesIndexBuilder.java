package com.indeed.jiraactions.jiraissues;

import com.google.common.base.Stopwatch;
import com.indeed.jiraactions.JiraActionsIndexBuilderConfig;
import com.indeed.jiraactions.JiraActionsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class JiraIssuesIndexBuilder {
    private static final Logger log = LoggerFactory.getLogger(JiraIssuesIndexBuilder.class);

    private final JiraActionsIndexBuilderConfig config;
    private final JiraIssuesParser parser;
    private final JiraIssuesFileWriter fileWriter;
    private final JiraIssuesProcess process;
    private long downloadTime = 0;
    private long processTime = 0;
    private long uploadTime = 0;

    public JiraIssuesIndexBuilder(final JiraActionsIndexBuilderConfig config, final List<String[]> issues) {
        this.config = config;
        fileWriter = new JiraIssuesFileWriter(config);
        process = new JiraIssuesProcess();
        parser = new JiraIssuesParser(fileWriter, process, issues);
    }

    public void run() throws Exception {
        try {
            final String userPass = config.getIuploadUsername() + ":" + config.getIuploadPassword();

            final Stopwatch downloadStopwatch = Stopwatch.createStarted();
            log.info("Downloading previous day's TSV.");
            fileWriter.downloadTsv();
            this.downloadTime = downloadStopwatch.elapsed(TimeUnit.MILLISECONDS);

            final Stopwatch stopwatch = Stopwatch.createStarted();
            parser.setupParser();
            fileWriter.createTsvAndSetHeaders();
            log.debug("{} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

            final Stopwatch processStopwatch = Stopwatch.createStarted();
            log.info("Updating TSV file.");
            parser.parseTsv();
            process.checkAndAddNewFields();
            this.processTime = processStopwatch.elapsed(TimeUnit.MILLISECONDS);
            log.debug("{} ms to update", processTime);
            processStopwatch.stop();

            final Stopwatch uploadStopwatch = Stopwatch.createStarted();
            fileWriter.uploadTsv();
            this.uploadTime = uploadStopwatch.elapsed(TimeUnit.MILLISECONDS);
            uploadStopwatch.stop();

        } catch (final Exception e) {
            log.error("Threw an exception trying to run the index builder", e);
            throw e;
        }
    }

    public long getDownloadTime() {
        return downloadTime;
    }

    public long getProcessTime() {
        return processTime;
    }

    public long getUploadTime() {
        return uploadTime;
    }
}
