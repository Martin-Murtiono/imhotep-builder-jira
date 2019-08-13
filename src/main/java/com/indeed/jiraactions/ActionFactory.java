package com.indeed.jiraactions;

import com.google.common.collect.ImmutableList;
import com.indeed.jiraactions.api.customfields.CustomFieldApiParser;
import com.indeed.jiraactions.api.customfields.CustomFieldDefinition;
import com.indeed.jiraactions.api.links.LinkFactory;
import com.indeed.jiraactions.api.response.issue.Issue;
import com.indeed.jiraactions.api.response.issue.User;
import com.indeed.jiraactions.api.response.issue.changelog.histories.History;
import com.indeed.jiraactions.api.response.issue.fields.comment.Comment;
import com.indeed.jiraactions.api.statustimes.StatusTime;
import com.indeed.jiraactions.api.statustimes.StatusTimeFactory;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ActionFactory {
    final private static List<String> DLT_RESOLUTIONS = ImmutableList.of("Fixed", "Done");
    final private static List<String> DLT_ISSUES = ImmutableList.of("Bug", "Improvement", "New Feature");

    private final UserLookupService userLookupService;
    private final CustomFieldApiParser customFieldParser;
    private final JiraActionsIndexBuilderConfig config;
    private final LinkFactory linkFactory = new LinkFactory();
    private final String[] dltStatuses;

    @SuppressWarnings("WeakerAccess")
    public ActionFactory(final UserLookupService userLookupService,
                         final CustomFieldApiParser customFieldApiParser,
                         final JiraActionsIndexBuilderConfig config) {
        this.userLookupService = userLookupService;
        this.customFieldParser = customFieldApiParser;
        this.config = config;
        this.dltStatuses = config.getDeliveryLeadTimeFields().split(",");
    }

    public Action create(final Issue issue) throws IOException {
        final User assignee = userLookupService.getUser(issue.initialValueKey("assignee", "assigneekey"));
        final User reporter = userLookupService.getUser(issue.initialValueKey("reporter", "reporterkey"));
        final ImmutableAction.Builder builder = ImmutableAction.builder()
                .action("create")
                .actor(issue.fields.creator == null ? User.INVALID_USER : issue.fields.creator)
                .assignee(assignee)
                .fieldschanged("created")
                .issueage(0)
                .issuekey(issue.key)
                .issuetype(issue.initialValue("issuetype"))
                .priority(issue.initialValue("priority"))
                .project(issue.initialValue("project"))
                .projectkey(issue.initialValue("projectkey"))
                .prevstatus("")
                .reporter(reporter)
                .resolution(issue.initialValue("resolution"))
                .status(issue.initialValue("status"))
                .summary(issue.initialValue("summary"))
                .timeinstate(0)
                .timesinceaction(0)
                .timestamp(issue.fields.created)
                .category(issue.initialValue("category"))
                .fixversions(issue.initialValue("fixversions"))
                .dueDate(issue.initialValue("duedate"))
                .components(issue.initialValue("components"))
                .labels(issue.initialValue("labels"))
                .createdDate(issue.fields.created.toString("yyyy-MM-dd"))
                .createdDateLong(Long.parseLong(issue.fields.created.toString("yyyyMMdd")))
                .lastUpdated(0)
                .closedDate(0)
                .resolutionDate(getDateResolved(issue.initialValue("resolutiondate")))
                .comments(0)
                .deliveryLeadTime(0)
                .statusTimes(StatusTimeFactory.firstStatusTime(issue.initialValue("status")))
                .statusHistory(createStatusHistory(issue.initialValue("status")))
                .links(Collections.emptySet());

            for (final CustomFieldDefinition customFieldDefinition : config.getCustomFields()) {
                builder.putCustomFieldValues(customFieldDefinition, customFieldParser.parseInitialValue(customFieldDefinition, issue));
            }

        return builder.build();
    }

    public Action update(final Action prevAction, final History history) {
        final User assignee = history.itemExist("assignee")
                ? userLookupService.getUser(history.getItemLastValueKey("assignee"))
                : prevAction.getAssignee();
        final User reporter = history.itemExist("reporter")
                ? userLookupService.getUser(history.getItemLastValueKey("reporter"))
                : prevAction.getReporter();
        final ImmutableAction.Builder builder = ImmutableAction.builder()
                .action("update")
                .actor(history.author == null ? User.INVALID_USER: history.author)
                .assignee(assignee)
                .fieldschanged(history.getChangedFields())
                .issueage(prevAction.getIssueage() + getTimeDiff(prevAction.getTimestamp(), history.created))
                .issuekey(prevAction.getIssuekey())
                .issuetype(history.itemExist("issuetype") ? history.getItemLastValue("issuetype") : prevAction.getIssuetype())
                .priority(history.itemExist("priority") ? history.getItemLastValue("priority") : prevAction.getPriority())
                .project(history.itemExist("project") ? history.getItemLastValue("project") : prevAction.getProject())
                .projectkey(history.itemExist("projectkey") ? history.getItemLastValue("projectkey") : prevAction.getProjectkey())
                .prevstatus(prevAction.getStatus())
                .reporter(reporter)
                .resolution(history.itemExist("resolution") ? history.getItemLastValue("resolution") : prevAction.getResolution())
                .status(history.itemExist("status") ? history.getItemLastValue("status") : prevAction.getStatus())
                .summary(history.itemExist("summary") ? history.getItemLastValue("summary") : prevAction.getSummary())
                .timeinstate(timeInState(prevAction, history))
                .timesinceaction(getTimeDiff(prevAction.getTimestamp(), history.created))
                .timestamp(history.created)
                .category(history.itemExist("category") ? history.getItemLastValue("category") : prevAction.getCategory())
                .fixversions(history.itemExist("fixversions") ? history.getItemLastValue("fixversions") : prevAction.getFixversions())
                .dueDate(history.itemExist("duedate") ? history.getItemLastValue("duedate").replace(" 00:00:00.0", "") : prevAction.getDueDate())
                .components(history.itemExist("components") ? history.getItemLastValue("components") : prevAction.getComponents())
                .labels(history.itemExist("labels") ? history.getItemLastValue("labels") : prevAction.getLabels())
                .createdDate(prevAction.getCreatedDate())
                .createdDateLong(prevAction.getCreatedDateLong())
                .closedDate(getDateClosed(prevAction, history))
                .resolutionDate(history.itemExist("resolutiondate") ? getDateResolved(history.getItemLastValue("resolutiondate")) : prevAction.getResolutionDate())
                .lastUpdated(0) // This field is used internally to filter issues longer than 6 months. It's only used by jiraissues so it will always go through the toCurrent() method where it takes the date of the previous action.
                .comments(prevAction.getComments())
                .deliveryLeadTime(0)
                .links(linkFactory.mergeLinks(prevAction.getLinks(), history.getAllItems("link")))
                .statusTimes(StatusTimeFactory.getStatusTimeUpdate(prevAction.getStatusTimes(), history, prevAction))
                .statusHistory(addStatusHistory(prevAction.getStatusHistory(), prevAction, history.itemExist("status") ? history.getItemLastValue("status") : prevAction.getStatus()));
        for (final CustomFieldDefinition customFieldDefinition : config.getCustomFields()) {
            builder.putCustomFieldValues(customFieldDefinition, customFieldParser.parseNonInitialValue(customFieldDefinition, prevAction, history));
        }

        return builder.build();
    }

    public Action comment(final Action prevAction, final Comment comment) {
        return ImmutableAction.builder()
                .from(prevAction)
                .action("comment")
                .actor(comment.author == null ? User.INVALID_USER : comment.author)
                .fieldschanged("comment")
                .issueage(prevAction.getIssueage() + getTimeDiff(prevAction.getTimestamp(), comment.created))
                .timeinstate(timeInState(prevAction, comment))
                .timesinceaction(getTimeDiff(prevAction.getTimestamp(), comment.created))
                .timestamp(comment.created)
                .comments(prevAction.getComments() + 1)
                .statusTimes(StatusTimeFactory.getStatusTimeComment(prevAction.getStatusTimes(), comment, prevAction))
                .build();
    }

    public Action toCurrent(final Action prevAction) {
        return ImmutableAction.builder()
                .from(prevAction)
                .issueage(prevAction.getIssueage() + getTimeDiff(prevAction.getTimestamp(), JiraActionsUtil.parseDateTime(config.getEndDate())))
                .timestamp(JiraActionsUtil.parseDateTime(config.getStartDate()))
                .lastUpdated(Integer.parseInt(prevAction.getTimestamp().toString("yyyyMMdd")))
                .statusTimes(StatusTimeFactory.getStatusTimeCurrent(prevAction.getStatusTimes(), prevAction, JiraActionsUtil.parseDateTime(config.getEndDate())))
                .deliveryLeadTime(getDeliveryLeadTime(StatusTimeFactory.getStatusTimeCurrent(prevAction.getStatusTimes(), prevAction, JiraActionsUtil.parseDateTime(config.getEndDate())), prevAction))
                .build();
    }

    private long timeInState(final Action prevAction, final Comment comment) {
        return timeInState(prevAction, comment.created);
    }

    private long timeInState(final Action prevAction, final History history) {
        return timeInState(prevAction, history.created);
    }

    private long timeInState(final Action prevAction, final DateTime changeTimestamp) {
        if (!Objects.equals(prevAction.getPrevstatus(), prevAction.getStatus())) {
            return getTimeDiff(prevAction.getTimestamp(), changeTimestamp);
        }

        return getTimeDiff(prevAction.getTimestamp(), changeTimestamp) + prevAction.getTimeinstate();
    }

    private long getDateResolved(final String resolutionDate) {
        if (resolutionDate == null || resolutionDate.equals("")) {
            return 0;
        } else {
            return Long.parseLong(resolutionDate.split("T")[0].replaceAll("[^\\d]", ""));
        }
    }

    private long getDateClosed(final Action prevAction, final History history) {
        final String status = history.itemExist("status") ? history.getItemLastValue("status") : prevAction.getStatus();
        if (status.equals("Closed")) {
            if (prevAction.getStatus().equals(status)) {
                return prevAction.getClosedDate();
            }
            return Integer.parseInt(history.created.toDateTimeISO().toString("yyyyMMdd"));
        }
        return 0;
    }

    private long getTimeDiff(final DateTime before, final DateTime after) {
        return (after.getMillis() - before.getMillis()) / 1000;
    }

    private List<String> createStatusHistory(final String status) {
        final List<String> statusHistory = new ArrayList<>();
        statusHistory.add(status);
        return statusHistory;
    }

    private List<String> addStatusHistory(final List<String> prevHistory, final Action prevAction, final String status) {
        final List<String> statusHistory = new ArrayList<>(prevHistory);
        if (!status.equals(prevAction.getStatus())) {
            statusHistory.add(status);
        }
        return statusHistory;
    }

    private long getDeliveryLeadTime(final Map<String, StatusTime> statusTimes, final Action action) {
        if (action.getStatus().equals("Closed") &&
        (DLT_RESOLUTIONS.contains(action.getResolution()) &&
        (DLT_ISSUES.contains(action.getIssuetype())))) {
            long deliveryLeadTime = 0;
            for (final String status : dltStatuses) {
                if (statusTimes.containsKey(status)) {
                    deliveryLeadTime += statusTimes.get(status).getTimeinstatus();
                }
            }
            return deliveryLeadTime;
        }
        return 0;
    }

}
