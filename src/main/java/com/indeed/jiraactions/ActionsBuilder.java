package com.indeed.jiraactions;

import com.indeed.jiraactions.api.response.issue.Issue;
import com.indeed.jiraactions.api.response.issue.changelog.ChangeLog;
import com.indeed.jiraactions.api.response.issue.changelog.histories.History;
import com.indeed.jiraactions.api.response.issue.fields.comment.Comment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.joda.time.DateTime;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ActionsBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(ActionsBuilder.class);

    private final Issue issue;
    private final DateTime startDate;
    private final ActionFactory actionFactory;
    private final List<History> histories;
    private final List<Comment> comments;
    private final List<Action> actions;


    public ActionsBuilder(final ActionFactory actionFactory, final Issue issue, final DateTime startDate) {
        this.actionFactory = actionFactory;
        this.issue = issue;
        this.startDate = startDate;

        actions = new ArrayList<>(issue.changelog.histories.length + issue.fields.comment.comments.length);
        histories = sortLatestHistories(issue);
        comments = sortLatestComments(issue);
    }

    @Nonnull
    public List<Action> buildActions() throws IOException {
        setCreateAction();
        compare();
        setActionToCurrent();
        return actions;
    }

    //
    // For Create Action
    //

    private void setCreateAction() throws IOException {
        actions.add(actionFactory.create(issue));
    }

    //
    // For Update Action
    //

    private void setUpdateActions(int index) {
        issue.changelog.sortHistories();
        actions.set(0, actionFactory.update(actions.get(0), histories.get(index)));
    }

    //
    // For Comment Action
    //

    private void setCommentActions(int index) {
        issue.fields.comment.sortComments();
        actions.set(0, actionFactory.comment(actions.get(0), comments.get(index)));
    }

    //
    // Updating Action to Given Date
    //

    private void setActionToCurrent() {
        actions.set(0, actionFactory.toCurrent(actions.get(0)));
    }

    private void compare() {
        int historyIndex = 0;
        int commentIndex = 0;
        for(Comment comment : comments) {
            if (comment.isBefore(actions.get(0).getTimestamp())) {
                LOG.debug("Skipping comment {} on {} because it's before the issue was created.", comment.id, issue.key);
                commentIndex++;
            }
        }
        while (true) {
            if (historyIndex >= histories.size() || commentIndex >= comments.size()) {
                if (commentIndex >= comments.size()) {
                    if (historyIndex >= histories.size()) {
                        break;
                    } else {
                        setUpdateActions(historyIndex);
                        historyIndex++;
                    }
                } else {
                    setCommentActions(commentIndex);
                    commentIndex++;
                }
            } else {
                if (histories.get(historyIndex).created.isBefore(comments.get(commentIndex).created)) {
                    setUpdateActions(historyIndex);
                    historyIndex++;
                } else {
                    setCommentActions(commentIndex);
                    commentIndex++;
                }
            }
        }
    }

    private List<History> sortLatestHistories(final Issue issue) {
        issue.changelog.sortHistories();
        final History[] histories = issue.changelog.histories;
        return Arrays.stream(histories).filter(a -> a.isBefore(startDate)).collect(Collectors.toList());
    }

    private List<Comment> sortLatestComments(final Issue issue) {
        issue.fields.comment.sortComments();
        final Comment[] comments = issue.fields.comment.comments;
        return Arrays.stream(comments).filter(a -> a.isBefore(startDate)).collect(Collectors.toList());
    }

}
