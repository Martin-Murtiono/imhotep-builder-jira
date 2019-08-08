package com.indeed.jiraactions;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.indeed.jiraactions.api.ApiCaller;
import com.indeed.jiraactions.api.ApiUserLookupService;
import com.indeed.jiraactions.api.IssuesAPICaller;
import com.indeed.jiraactions.api.customfields.CustomFieldApiParser;
import com.indeed.jiraactions.api.customfields.CustomFieldDefinition;
import com.indeed.jiraactions.api.links.LinkTypesApiCaller;
import com.indeed.jiraactions.api.statustimes.StatusTypesApiCaller;
import com.indeed.jiraactions.jiraissues.JiraIssuesIndexBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class JiraActionsIndexBuilder {
    private static final Logger log = LoggerFactory.getLogger(JiraActionsIndexBuilder.class);

    private final JiraActionsIndexBuilderConfig config;

    public JiraActionsIndexBuilder(final JiraActionsIndexBuilderConfig config) {
        this.config = config;
    }

    public void run() throws Exception {
        try {
            final Stopwatch stopwatch = Stopwatch.createStarted();

            final ApiCaller apiCaller = new ApiCaller(config);

            final ApiUserLookupService userLookupService = new ApiUserLookupService(config, apiCaller);
            final CustomFieldApiParser customFieldApiParser = new CustomFieldApiParser(userLookupService);
            final ActionFactory actionFactory = new ActionFactory(userLookupService, customFieldApiParser, config);

            final IssuesAPICaller issuesAPICaller = new IssuesAPICaller(config, apiCaller);
            initializeIssuesApiCaller(issuesAPICaller);

            if(!issuesAPICaller.currentPageExist()) {
                log.warn("No issues found for this time range.");
                return;
            }

            long fileTime = 0;

            final DateTime startDate = JiraActionsUtil.parseDateTime(config.getStartDate());
            final DateTime endDate = JiraActionsUtil.parseDateTime(config.getEndDate());

            if (!startDate.isBefore(endDate)) {
                log.error("Invalid start date '{}' not before end date '{}'", startDate, endDate);
            }

            final LinkTypesApiCaller linkTypesApiCaller = new LinkTypesApiCaller(config, apiCaller);
            final List<String> linkTypes = linkTypesApiCaller.getLinkTypes();
            final StatusTypesApiCaller statusTypesApiCaller = new StatusTypesApiCaller(config, apiCaller);
            final List<String> statusTypes = statusTypesApiCaller.getStatusTypes();

            final TsvFileWriter writer = new TsvFileWriter(config, linkTypes, statusTypes);
            final Stopwatch headerStopwatch = Stopwatch.createStarted();
            writer.createFileAndWriteHeaders();
            headerStopwatch.stop();
            fileTime += headerStopwatch.elapsed(TimeUnit.MILLISECONDS);

            final ApiPageProvider apiPageProvider = new ApiPageProvider(issuesAPICaller, actionFactory, config, writer);
            final Paginator paginator = new Paginator(apiPageProvider, startDate, endDate, config.getJiraissues());

            paginator.process();
            fileTime += apiPageProvider.getFileWritingTime();
            final long apiTime = apiPageProvider.getApiTime();
            final long processTime = apiPageProvider.getProcessingTime();


            log.debug("Had to look up {} users.", userLookupService.numLookups());

            final Set<CustomFieldDefinition> missedFieldDefinitions =
                    Sets.difference(
                            ImmutableSet.copyOf(config.getCustomFields()),
                            apiPageProvider.getCustomFieldsSeen()
                    );
            final List<String> missedFields = missedFieldDefinitions.stream()
                    .map(CustomFieldDefinition::getName)
                    .collect(Collectors.toList());

            log.debug("No values seen for these custom fields: " + missedFields);

            final Stopwatch jiraIssuesStopwatch = Stopwatch.createStarted();
            final JiraIssuesIndexBuilder jiraIssuesIndexBuilder = new JiraIssuesIndexBuilder(config, writer.getIssues());
            if(config.getJiraissues()) {
                log.info("Building jiraissues with {} new/updated issues.", writer.getIssues().size());
                jiraIssuesIndexBuilder.run();
            } else {
                log.info("Not building jiraissues.");
            }
            jiraIssuesStopwatch.stop();

            final Stopwatch fileUploadStopwatch = Stopwatch.createStarted();
            writer.uploadTsvFile();
            fileUploadStopwatch.stop();
            log.debug("{} ms to create and upload TSV.", fileUploadStopwatch.elapsed(TimeUnit.MILLISECONDS));

            stopwatch.stop();

            final long apiUserTime = userLookupService.getUserLookupTotalTime();

            log.info("{} ms to build Jiraactions.", stopwatch.elapsed(TimeUnit.MILLISECONDS)-jiraIssuesStopwatch.elapsed(TimeUnit.MILLISECONDS));
            log.info("Jiraactions:{apiTime: {} ms, processTime: {} ms, fileTime: {} ms, userLookupTime: {} ms}",
                    apiTime-apiUserTime, processTime, fileTime, apiUserTime);
            if(config.getJiraissues()) {
                log.info("{} ms to build Jiraissues.", jiraIssuesStopwatch.elapsed(TimeUnit.MILLISECONDS));
                log.info("Jiraissues:{downloadTime: {} ms, processTime: {} ms, uploadTime: {} ms}",
                        jiraIssuesIndexBuilder.getDownloadTime(), jiraIssuesIndexBuilder.getProcessTime(), jiraIssuesIndexBuilder.getUploadTime());
            }
            log.info("{} ms for the whole process.", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        } catch (final Exception e) {
            log.error("Threw an exception trying to run the index builder", e);
            throw e;
        }
    }

    private void initializeIssuesApiCaller(final IssuesAPICaller issuesAPICaller) throws IOException {
        final long start = System.currentTimeMillis();
        final int total = issuesAPICaller.setNumTotal();
        final long end = System.currentTimeMillis();
        log.debug("{} ms, found {} total issues.", end - start, total);
    }
}
