package com.indeed.skeleton.index.builder.jiraaction;

import com.indeed.skeleton.index.builder.jiraaction.api.response.issue.Issue;
import com.indeed.skeleton.index.builder.jiraaction.api.response.issue.changelog.histories.History;
import com.indeed.skeleton.index.builder.jiraaction.api.response.issue.fields.comment.Comment;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Created by soono on 8/30/16.
 */
public class ActionsBuilder {
    private final Issue issue;
    private boolean isNewIssue;
    public final List<Action> actions = new ArrayList<Action>();

    public ActionsBuilder(final Issue issue) throws ParseException {
        this.issue = issue;
        setIsNewIssue();
    }

    public List<Action> buildActions() throws Exception {

        if (isNewIssue) setCreateAction();

        setUpdateActions();

        setCommentActions();

        return actions;
    }

    private void setIsNewIssue() throws ParseException {
        this.isNewIssue = isOnYesterday(issue.fields.created);
    }

    //
    // For Create Action
    //

    private void setCreateAction() throws Exception {
        final Action createAction = new Action(issue);
        actions.add(createAction);
    }

    //
    // For Update Action
    //

    private void setUpdateActions() throws Exception {
        issue.changelog.sortHistories();

        Action prevAction = getLatestAction();
        for (final History history : issue.changelog.histories) {
            if (!isOnYesterday(history.created)) continue;
            final Action updateAction = new Action(prevAction, history);
            actions.add(updateAction);
            prevAction = updateAction;
        }
    }

    private Action getLatestAction() throws Exception {
        if (isNewIssue) return actions.get(0); // returns create action.
        else {
            // Get the last action by day before yesterday.
            Action action = new Action(issue);
            for (final History history : issue.changelog.histories) {
                if (isOnYesterday(history.created)) break;
                action = new Action(action, history);
            }
            return action;
        }
    }

    //
    // For Comment Action
    //

    private void setCommentActions() throws Exception {
        issue.fields.comment.sortComments();

        int currentActionIndex = 0;
        for (final Comment comment : issue.fields.comment.comments) {
            if (!isOnYesterday(comment.created)) continue;
            if (actions.isEmpty() || !commentIsAfter(comment, actions.get(0))) {
                final Action commentAction = new Action(getLatestAction(), comment);
                actions.add(0, commentAction);
            } else {
                while(true) {
                    if(commentIsRightAfter(comment, currentActionIndex)) {
                        final Action commentAction = new Action(actions.get(currentActionIndex), comment);
                        actions.add(currentActionIndex+1, commentAction);
                        break;
                    } else {
                        currentActionIndex++;
                    }
                }
            }
        }
    }


    //
    // For interpreting if the date is new
    //

    private boolean isOnYesterday(final String dateString) throws ParseException {
        final Calendar date = Calendar.getInstance();
        date.setTime(parseDate(dateString));

        final Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);

        return date.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR)
                && date.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR);
    }

    private boolean commentIsAfter(final Comment comment, final Action action) throws ParseException {
        // return true if comment is made after the action
        final Date commentDate = parseDate(comment.created);
        final Date actionDate = parseDate(action.timestamp);
        return commentDate.after(actionDate);
    }

    private boolean commentIsRightAfter(final Comment comment, final int actionIndex) throws ParseException {
        final Action action = actions.get(actionIndex);
        final int nextIndex = actionIndex + 1;
        final Action nextAction = actions.size() > nextIndex ? actions.get(nextIndex) : null;
        return commentIsAfter(comment, action) &&
                ( nextAction == null || !commentIsAfter(comment, nextAction) );
    }

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
    private Date parseDate(final String dateString) throws ParseException {
        final String strippedCreatedString = dateString.replace('T', ' ');
        final Date date = DATE_FORMAT.parse(strippedCreatedString);
        return date;
    }
}
