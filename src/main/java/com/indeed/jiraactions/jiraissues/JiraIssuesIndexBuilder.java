package com.indeed.jiraactions.jiraissues;

import com.google.common.base.Stopwatch;
import com.indeed.jiraactions.JiraActionsIndexBuilderCommandLine;
import com.indeed.jiraactions.JiraActionsIndexBuilderConfig;
import com.indeed.jiraactions.JiraActionsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class JiraIssuesIndexBuilder {
    private static final Logger log = LoggerFactory.getLogger(JiraIssuesIndexBuilder.class);

    private final JiraActionsIndexBuilderConfig config;
    private long downloadTime = 0;
    private long processTime = 0;
    private long uploadTime = 0;

    public JiraIssuesIndexBuilder(JiraActionsIndexBuilderConfig config) {
        this.config = config;
    }

    public void run() throws Exception {
        final JiraIssuesFileWriter fileWriter = new JiraIssuesFileWriter(config);
        try {

            final Stopwatch downloadStopwatch = Stopwatch.createStarted();
            log.info("Downloading previous day's TSV.");
            fileWriter.downloadTsv(JiraActionsUtil.parseDateTime(config.getStartDate()));
            this.downloadTime = downloadStopwatch.elapsed(TimeUnit.MILLISECONDS);

            fileWriter.parseNewTsv();

            fileWriter.createTsv(JiraActionsUtil.parseDateTime(config.getStartDate()));
            log.info("Jiraissues file successfully created.");

            final Stopwatch processStopwatch = Stopwatch.createStarted();
            log.debug("Updating TSV file.");
            fileWriter.process();
            this.processTime = processStopwatch.elapsed(TimeUnit.MILLISECONDS);
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
        return this.downloadTime;
    }

    public long getProcessTime() {
        return this.processTime;
    }

    public long getUploadTime() {
        return this.uploadTime;
    }
}
