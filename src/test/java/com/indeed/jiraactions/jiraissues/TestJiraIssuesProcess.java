package com.indeed.jiraactions.jiraissues;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TestJiraIssuesProcess {
    JiraIssuesProcess process = new JiraIssuesProcess();
    List<String[]> newIssues = new ArrayList<>();
    String[] fields = {"issuekey", "status", "time", "issueage", "totaltime_open", "totaltime_pending_triage", "totaltime_in_progress", "totaltime_closed"};

    @Before
    public void setup() {
        setupNewIssues();

        process.setNewIssues(newIssues);
        process.setFields(Arrays.stream(fields).collect(Collectors.toList()));
        process.setOldFields(Arrays.stream(fields).collect(Collectors.toList()));
        process.convertToMap();
    }

    @Test
    public void testCompare() {
        // The issues being passed in would be the old issues from the previous day.
        String[] issue1 = {"A", "Pending Triage", "0", "0", "0", "0", "0", "0"};   // Test Replacing Process
        Map<String, String> output1 = process.compareAndUpdate(issue1);
        String[] expected1 = {"A", "In Progress", "86400", "86400", "0", "86400", "0", "0"};
        Assert.assertEquals(expected1, output1.values().toArray());

        String[] issue2 = {"B", "Closed", "0", "0", "0", "0", "0", "0"};  // Test Updating Process - Although we could have tested the actual update method, this also checks if there is a new instance of that issue and would be a better case.
        Map<String, String> output2 = process.compareAndUpdate(issue2);
        String[] expected2 = {"B", "Closed", "86400", "86400", "0", "0", "0", "86400"};
        Assert.assertEquals(expected2, output2.values().toArray());

        String[] issue3 = {"", "", "0", "0", "0", "0", "0", "0"};       // Test blank issuekey and status
        Map<String, String> output3 = process.compareAndUpdate(issue3);
        String[] expected3 = {"", "", "86400", "86400", "0", "0", "0", "0"};
        Assert.assertEquals(expected3, output3.values().toArray());
    }

    @Test
    public void testGetRemainingIssues() {
        String[] issue1 = {"A", "Pending Triage", "0", "0", "0", "0", "0", "0"};
        String[] issue2 = {"B", "Closed", "0", "0", "0", "0", "0", "0"};
        process.compareAndUpdate(issue1);   // The issue is removed if it is replaced when passed in the compare method.
        process.compareAndUpdate(issue2);

        List<Map<String, String>> remainingIssues = process.getRemainingIssues();
        Assert.assertEquals(1, remainingIssues.size());

        Map<String, String> remainingIssue = remainingIssues.get(0);
        String[] expectedIssue = {"C", "Open", "86400", "86400", "0", "0", "0", "0"};
        Assert.assertEquals(expectedIssue, remainingIssue.values().toArray());
    }

    @Test
    public void testNonApiStatuses() {
        String[] issue = {"D", "Accepted", "0", "0", "0", "0", "0", "0"};       // "Accepted" is in the API but it isn't in the fields that were set for these tests
        process.compareAndUpdate(issue);
        Assert.assertEquals("Accepted", process.getNonApiStatuses().get(0));
    }

    @Test
    public void testNewFields() {
        JiraIssuesProcess process1 = new JiraIssuesProcess();

        List<String[]> newIssues = new ArrayList<>();
        String[] newFields = {"issuekey", "status", "time", "issueage", "totaltime_closed", "totaltime_open"};
        newIssues.add(newFields);

        String[] oldFields = {"issuekey", "status", "time", "issueage", "totaltime_open"};
        process1.setNewIssues(newIssues);
        process1.setOldFields(Arrays.stream(oldFields).collect(Collectors.toList()));
        process1.setFields(Arrays.stream(newFields).collect(Collectors.toList()));
        process1.checkAndAddNewFields();
        Assert.assertEquals("totaltime_closed", process1.getNewFields().get(0));

        String[] issue = {"A", "Open", "0", "0", "0"};
        Map<String, String> output = process1.compareAndUpdate(issue);
        String[] expected = {"A", "Open", "86400", "86400", "0", "86400"};      // If there is a new field it will set "0" as the value for that field
        Assert.assertEquals(expected, output.values().toArray());
    }

    public void setupNewIssues() {
        newIssues.add(fields);
        String[] issue1 = {"A", "In Progress", "86400", "86400", "0", "86400", "0", "0"};       // Should replace previous day's issue
        newIssues.add(issue1);
        String[] issue2 = {"C", "Open", "86400", "86400", "0", "0", "0", "0"};      // Should be added
        newIssues.add(issue2);
    }

}
