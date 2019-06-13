package com.indeed.jiraactions;

import com.indeed.jiraactions.api.customfields.CustomFieldApiParser;
import com.indeed.jiraactions.api.customfields.CustomFieldDefinition;
import com.indeed.jiraactions.api.links.LinkFactory;
import com.indeed.jiraactions.api.response.issue.Issue;
import com.indeed.jiraactions.api.response.issue.User;
import com.indeed.jiraactions.api.response.issue.changelog.histories.History;
import com.indeed.jiraactions.api.response.issue.fields.comment.Comment;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.Objects;

public class ActionFactory {

    private final UserLookupService userLookupService;
    private final CustomFieldApiParser customFieldParser;
    private final JiraActionsIndexBuilderConfig config;
    private final LinkFactory linkFactory = new LinkFactory();

    @SuppressWarnings("WeakerAccess")
    public ActionFactory(final UserLookupService userLookupService,
                         final CustomFieldApiParser customFieldApiParser,
                         final JiraActionsIndexBuilderConfig config) {
        this.userLookupService = userLookupService;
        this.customFieldParser = customFieldApiParser;
        this.config = config;
    }

    public Action create(final Issue issue) throws IOException {
        final User assignee = userLookupService.getUser(issue.initialValueKey("assignee", "assigneekey"));
        final User reporter = userLookupService.getUser(issue.initialValueKey("reporter", "reporterkey"));
        final ImmutableAction.Builder builder = ImmutableAction.builder()
                .issuekey(issue.key)
                .actor(issue.fields.creator == null ? User.INVALID_USER : issue.fields.creator)
                .assignee(assignee)
                .issueage(0)
                .issuetype(issue.initialValue("issuetype"))
                .priority(issue.initialValue("priority"))
                .project(issue.initialValue("project"))
                .projectkey(issue.initialValue("projectkey"))
                .reporter(reporter)
                .resolution(issue.initialValue("resolution"))
                .status(issue.initialValue("status"))
                .timeinstatus(0)
                .summary(issue.initialValue("summary"))
                .timestamp(issue.fields.created)
                .category(issue.initialValue("category"))
                .fixversions(issue.initialValue("fixversions"))
                .dueDate(issue.initialValue("duedate"))
                .components(issue.initialValue("components"))
                .labels(issue.initialValue("labels"))
                .createdDate(issue.fields.created.toString("yyyy-MM-dd"))
                .comments(0)
                .dateResolved("")
                .dateClosed("")
                .links(Collections.emptySet());

            for(final CustomFieldDefinition customFieldDefinition : config.getCustomFields()) {
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
                .issuekey(prevAction.getIssuekey())
                .actor(history.author == null ? User.INVALID_USER: history.author)
                .assignee(assignee)
                .issueage(prevAction.getIssueage() + getTimeDiff(prevAction.getTimestamp(), history.created))
                .issuetype(history.itemExist("issuetype") ? history.getItemLastValue("issuetype") : prevAction.getIssuetype())
                .priority(history.itemExist("priority") ? history.getItemLastValue("priority") : prevAction.getPriority())
                .project(history.itemExist("project") ? history.getItemLastValue("project") : prevAction.getProject())
                .projectkey(history.itemExist("projectkey") ? history.getItemLastValue("projectkey") : prevAction.getProjectkey())
                .reporter(reporter)
                .resolution(history.itemExist("resolution") ? history.getItemLastValue("resolution") : prevAction.getResolution())
                .status(history.itemExist("status") ? history.getItemLastValue("status") : prevAction.getStatus())
                .timeinstatus(getTimeinstatus(prevAction, history))
                .summary(history.itemExist("summary") ? history.getItemLastValue("summary") : prevAction.getSummary())
                .timestamp(history.created)
                .category(history.itemExist("category") ? history.getItemLastValue("category") : prevAction.getCategory())
                .fixversions(history.itemExist("fixversions") ? history.getItemLastValue("fixversions") : prevAction.getFixversions())
                .dueDate(history.itemExist("duedate") ? history.getItemLastValue("duedate").replace(" 00:00:00.0", "") : prevAction.getDueDate())
                .components(history.itemExist("components") ? history.getItemLastValue("components") : prevAction.getComponents())
                .labels(history.itemExist("labels") ? history.getItemLastValue("labels") : prevAction.getLabels())
                .createdDate(prevAction.getCreatedDate())
                .dateResolved(dateResolved(prevAction, history))
                .dateClosed(dateClosed(prevAction, history))
                .comments(prevAction.getComments())
                .links(linkFactory.mergeLinks(prevAction.getLinks(), history.getAllItems("link")));

        for(final CustomFieldDefinition customFieldDefinition : config.getCustomFields()) {
            builder.putCustomFieldValues(customFieldDefinition, customFieldParser.parseNonInitialValue(customFieldDefinition, prevAction, history));
        }

        return builder.build();
    }

    public Action comment(final Action prevAction, final Comment comment) {
        return ImmutableAction.builder()
                .from(prevAction)
                .actor(comment.author == null ? User.INVALID_USER : comment.author)
                .issueage(prevAction.getIssueage() + getTimeDiff(prevAction.getTimestamp(), comment.created))
                .timeinstatus(prevAction.getTimeinstatus() + getTimeDiff(prevAction.getTimestamp(), comment.created))
                .timestamp(comment.created)
                .comments(prevAction.getComments()+1)
                .build();
    }

    public Action toCurrent(final Action prevAction) {
        return ImmutableAction.builder()
                .from(prevAction)
                .issueage(prevAction.getIssueage() + addCurrentTimeDiff(prevAction))
                .timeinstatus(prevAction.getTimeinstatus() + addCurrentTimeDiff(prevAction))
                //.timestamp(prevAction.getTimestamp())
                .timestamp(JiraActionsUtil.parseDateTime(config.getStartDate()))
                .build();
    }


    private String dateResolved(final Action prevAction, final History history) {
        String resolution = history.itemExist("resolution") ? history.getItemLastValue("resolution") : prevAction.getResolution();
        if (resolution.equals("Fixed")){
            return history.created.toDateTimeISO().toString();
        }
        return "";
    }

    private String dateClosed(final Action prevAction, final History history) {
        String status = history.itemExist("status") ? history.getItemLastValue("status") : prevAction.getResolution();
        if (status.equals("Closed")){
            return history.created.toDateTimeISO().toString();
        }
        return "";
    }

    private long getTimeDiff(final DateTime before, final DateTime after) {
        return (after.getMillis() - before.getMillis()) / 1000;
    }

    private long getTimeinstatus(final Action prevAction, final History history) {
        String status = history.itemExist("status") ? history.getItemLastValue("status") : prevAction.getStatus();
        if (!status.equals(prevAction.getStatus())) {
            return 0;
        }
        return prevAction.getTimeinstatus() + getTimeDiff(prevAction.getTimestamp(), history.created);
    }

    private long addCurrentTimeDiff(Action prevAction) {
        return getTimeDiff(prevAction.getTimestamp(), JiraActionsUtil.parseDateTime(config.getStartDate()));
    }
}
