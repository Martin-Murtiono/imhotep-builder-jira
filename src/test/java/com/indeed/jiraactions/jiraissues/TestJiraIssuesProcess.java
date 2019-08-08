package com.indeed.jiraactions.jiraissues;

import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TestJiraIssuesProcess {
    DateTime date = DateTime.parse("2019-01-01");
    JiraIssuesProcess process = new JiraIssuesProcess(date);
    List<String[]> newIssues = new ArrayList<>();
    String[] fields = {"issuekey", "status", "time", "issueage", "totaltime_open", "totaltime_pending_triage", "totaltime_in_progress", "totaltime_closed"};

    @Before
    public void setup() {
        setupNewIssues();

        process.setNewIssues(newIssues);
        process.setNewFields(Arrays.stream(fields).collect(Collectors.toList()));
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
    public void testNewFields() {
        JiraIssuesProcess process = new JiraIssuesProcess(date);

        List<String[]> newIssues = new ArrayList<>();
        String[] newFields = {"issuekey", "status", "time", "issueage", "totaltime_closed", "totaltime_open"};
        newIssues.add(newFields);

        String[] oldFields = {"issuekey", "status", "time", "issueage", "totaltime_open"};
        process.setNewIssues(newIssues);
        process.setOldFields(Arrays.stream(oldFields).collect(Collectors.toList()));
        process.setNewFields(Arrays.stream(newFields).collect(Collectors.toList()));

        String[] issue = {"A", "Open", "0", "0", "0"};
        Map<String, String> output = process.compareAndUpdate(issue);
        String[] expected = {"A", "Open", "86400", "86400", "0", "86400"};      // If there is a new field it will set "0" as the value for that field
        Assert.assertEquals(expected, output.values().toArray());
    }

    @Test
    public void testNonApiStatuses() {
        String[] issue = {"D", "Accepted", "0", "0", "0", "0", "0", "0"};       // "Accepted" is in the API but it isn't in the fields that were set for these tests
        process.compareAndUpdate(issue);
        Assert.assertEquals("Accepted", process.getNonApiStatuses().get(0));
    }

    @Test
    public void testStatusReplacement() {
        JiraIssuesProcess process = new JiraIssuesProcess(date);

        List<String[]> newIssues = new ArrayList<>();
        String[] newFields = {"issuekey", "status", "time", "issueage", "totaltime_c", "totaltime_a"};
        newIssues.add(newFields);

        String[] oldFields = {"issuekey", "status", "time", "issueage", "totaltime_a", "totaltime_b"};      // b is replaced by c and is placed in a different order
        process.setNewIssues(newIssues);
        process.setOldFields(Arrays.stream(oldFields).collect(Collectors.toList()));
        process.setNewFields(Arrays.stream(newFields).collect(Collectors.toList()));

        String[] issue = {"A", "a", "0", "0", "0", "1"};        // There currently isn't a way to check which statuses get replaced so the best it can do is "remove" the old one and set 0 as the new one
        Map<String, String> output = process.compareAndUpdate(issue);
        String[] expected = {"A", "a", "86400", "86400", "0", "86400"};
        Assert.assertEquals(expected, output.values().toArray());
    }

    @Test
    public void testDateFilter() {
        // start date is 2019-01-01
        JiraIssuesProcess process = new JiraIssuesProcess(date);

        List<String[]> newIssues = new ArrayList<>();
        String[] newFields = {"issuekey", "status", "time", "issueage", "closedate", "createdate"};
        newIssues.add(newFields);

        String[] oldFields = {"issuekey", "status", "time", "issueage", "closedate", "createdate"};
        process.setNewIssues(newIssues);
        process.setOldFields(Arrays.stream(oldFields).collect(Collectors.toList()));
        process.setNewFields(Arrays.stream(newFields).collect(Collectors.toList()));

        String[] issue1 = {"A", "Closed", "0", "0", "20180101", "20180101"};        // It will not write issues closed longer than 6 months before the start date - filtered.
        Map<String, String> output1 = process.compareAndUpdate(issue1);
        Assert.assertNull(output1);

        String[] issue2 = {"B", "Open", "0", "0", "0", "20180601"};     // Created within a year - not filtered.
        Map<String, String> output2 = process.compareAndUpdate(issue2);
        String[] expected2 = {"B", "Open", "86400", "86400", "0", "20180601"};
        Assert.assertEquals(expected2, output2.values().toArray());

        String[] issue3 = {"C", "Open", "0", "0", "0", "20170101"};     // Created over a year ago - filtered.
        Map<String, String> output3 = process.compareAndUpdate(issue3);
        Assert.assertNull(output3);

        String[] issue4 = {"D", "Closed", "0", "0", "20181201", "20150101"};    // Although it was created over a year ago, it was closed within 6 months of the start date - not filtered.
        Map<String, String> output4 = process.compareAndUpdate(issue4);
        String[] expected4 = {"D", "Closed", "86400", "86400", "20181201", "20150101"};
        Assert.assertEquals(expected4, output4.values().toArray());

    }

    public void setupNewIssues() {
        newIssues.add(fields);
        String[] issue1 = {"A", "In Progress", "86400", "86400", "0", "86400", "0", "0"};       // Should replace previous day's issue
        newIssues.add(issue1);
        String[] issue2 = {"C", "Open", "86400", "86400", "0", "0", "0", "0"};      // Should be added
        newIssues.add(issue2);
    }

}
