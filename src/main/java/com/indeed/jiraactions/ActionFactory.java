package com.indeed.jiraactions;

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
import java.util.Objects;

public class ActionFactory {

    private final UserLookupService userLookupService;
    private final CustomFieldApiParser customFieldParser;
    private final JiraActionsIndexBuilderConfig config;
    private final LinkFactory linkFactory = new LinkFactory();
    private final StatusTimeFactory statusTimeFactory = new StatusTimeFactory();

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
                .comments(0)
                .dateResolved("")
                .dateClosed("")
                .statustimes(statusTimeFactory.firstStatusTime(issue.initialValue("status")))
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
                .dateResolved(dateResolved(prevAction, history))
                .dateClosed(dateClosed(prevAction, history))
                .comments(prevAction.getComments())
                .statustimes(getStatusTime(prevAction.getStatustimes(), history, prevAction))
                .links(linkFactory.mergeLinks(prevAction.getLinks(), history.getAllItems("link")));

        for(final CustomFieldDefinition customFieldDefinition : config.getCustomFields()) {
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
                .comments(prevAction.getComments()+1)
                .statustimes(getStatusTime(prevAction.getStatustimes(), comment, prevAction))
                .build();
    }

    public Action toCurrent(final Action prevAction) {
        return ImmutableAction.builder()
                .from(prevAction)
                .issueage(prevAction.getIssueage() + getTimeDiff(prevAction.getTimestamp(), JiraActionsUtil.parseDateTime(config.getEndDate())))
                .timestamp(JiraActionsUtil.parseDateTime(config.getEndDate()))
                .statustimes(getStatusTime(prevAction.getStatustimes(), prevAction))
                .build();
    }

    private long timeInState(final Action prevAction, final Comment comment) {
        return timeInState(prevAction, comment.created);
    }

    private long timeInState(final Action prevAction, final History history) {
        return timeInState(prevAction, history.created);
    }

    private long timeInState(final Action prevAction, final DateTime changeTimestamp) {
        if(!Objects.equals(prevAction.getPrevstatus(), prevAction.getStatus())) {
            return getTimeDiff(prevAction.getTimestamp(), changeTimestamp);
        }

        return getTimeDiff(prevAction.getTimestamp(), changeTimestamp) + prevAction.getTimeinstate();
    }

    private List<StatusTime> getStatusTime(List<StatusTime> list, final History history, final Action prevAction) {
        final List<StatusTime> st = new ArrayList<>(list);
        String status = history.itemExist("status") ? history.getItemLastValue("status") : prevAction.getStatus();
        st.set(st.size()-1, statusTimeFactory.updateStatus(st.get(st.size()-1), getTimeDiff(prevAction.getTimestamp(), history.created)));
        if (!status.equals(prevAction.getStatus())) {
            boolean first = true;
            for(int i = 0; i < st.size(); i++) {
                if (st.get(i).getStatus().equals(status)) {
                    st.set(i, statusTimeFactory.updateTimeToLast(st.get(i)));
                    first = false;
                }
            }
            if (first) {
                st.add(statusTimeFactory.addStatus(status, prevAction.getIssueage() + getTimeDiff(prevAction.getTimestamp(), history.created), prevAction.getIssueage() + getTimeDiff(prevAction.getTimestamp(), history.created)));
            } else {
                st.add(statusTimeFactory.addStatus(status, 0, prevAction.getIssueage() + getTimeDiff(prevAction.getTimestamp(), history.created)));
            }

        }
        return st;
    }

    private List<StatusTime> getStatusTime(List<StatusTime> list, final Comment comment, final Action prevAction) {
        List<StatusTime> st = new ArrayList<>(list);
        st.set(st.size()-1, statusTimeFactory.updateStatus(st.get(st.size()-1), getTimeDiff(prevAction.getTimestamp(), comment.created)));
        return st;
    }

    private List<StatusTime> getStatusTime(List<StatusTime> list, final Action prevAction) {
        List<StatusTime> st = new ArrayList<>(list);
        st.set(st.size()-1, statusTimeFactory.updateStatus(st.get(st.size()-1), getTimeDiff(prevAction.getTimestamp(), JiraActionsUtil.parseDateTime(config.getEndDate()))));
        return st;
    }

    private String dateResolved(final Action prevAction, final History history) {
        String resolution = history.itemExist("resolution") ? history.getItemLastValue("resolution") : prevAction.getResolution();
        if (!resolution.isEmpty() && prevAction.getResolution().isEmpty()){
            return history.created.toDateTimeISO().toString();
        }
        return "";
    }

    private String dateClosed(final Action prevAction, final History history) {
        String status = history.itemExist("status") ? history.getItemLastValue("status") : prevAction.getStatus();
        if (status.equals("Closed")){
            return history.created.toDateTimeISO().toString();
        }
        return "";
    }

    private long getTimeDiff(final DateTime before, final DateTime after) {
        return (after.getMillis() - before.getMillis()) / 1000;
    }

}
