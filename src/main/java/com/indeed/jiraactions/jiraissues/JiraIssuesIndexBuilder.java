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

    public JiraIssuesIndexBuilder(JiraActionsIndexBuilderConfig config) {
        this.config = config;
    }

    public void run() throws Exception {
        final JiraIssuesFileWriter fileWriter = new JiraIssuesFileWriter(config);
        try {
            final Stopwatch stopwatch = Stopwatch.createStarted();

            log.info("Downloading previous day's TSV.");
            fileWriter.downloadTsv(JiraActionsUtil.parseDateTime(config.getStartDate()));
            log.debug("Took {} ms to download previous day's TSV.", stopwatch.elapsed(TimeUnit.MILLISECONDS));

            fileWriter.parseNewTsv();

            fileWriter.createTsv(JiraActionsUtil.parseDateTime(config.getStartDate()));
            log.info("Jiraissues file successfully created.");

            final Stopwatch processTime = Stopwatch.createStarted();
            log.debug("Updating TSV file.");
            fileWriter.process();
            log.debug("{} ms to update TSV.", processTime.elapsed(TimeUnit.MILLISECONDS));
            processTime.stop();

            final Stopwatch uploadTime = Stopwatch.createStarted();
            fileWriter.uploadTsv();
            log.debug("{} ms to upload TSV.", uploadTime.elapsed(TimeUnit.MILLISECONDS));
            uploadTime.stop();

            log.debug("{} ms to build jiraissues", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        } catch (final Exception e) {
            log.error("Threw an exception trying to run the index builder", e);
            throw e;
        }
    }
}
