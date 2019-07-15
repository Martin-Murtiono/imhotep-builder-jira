package com.indeed.jiraactions.jiraissues;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TestJiraIssuesProcess {
    JiraIssuesParser parser;
    JiraIssuesProcess process;
    List<String[]> newIssues = new ArrayList<>();

    @Before
    public void setup() {
        setupNewIssues();
        process = new JiraIssuesProcess();

        process.setNewIssues(newIssues);
        process.convertToMap();

        String[] headers = {"issuekey", "status", "time", "issueage", "totaltime_open", "totaltime_pending_triage", "totaltime_in_progress", "totaltime_closed"};
        process.setOldFields(Arrays.stream(headers).collect(Collectors.toList()));
    }

    @Test
    public void testCompare() {
        String[] issue1 = {"A", "Pending Triage", "0", "0", "0"};   // Test Replacing process
        Map<String, String> output1 = process.compareAndUpdate(issue1);
        String[] expected1 = {"A", "In Progress", "86400", "86400", "0", "86400", "0", "0"};
        Assert.assertEquals(expected1, output1.values().toArray());

        String[] issue2 = {"B", "Closed", "0", "0", "0", "0", "0", "0"};     // Test Updating process - Although we could have tested the actual update method, this also checks if there is a new instance of that issue and would be a better case.
        Map<String, String> output2 = process.compareAndUpdate(issue2);
        String[] expected2 = {"B", "Closed", "86400", "86400", "0", "0", "0", "86400"};
        Assert.assertEquals(expected2, output2.values().toArray());
    }

    @Test
    public void testGetRemainingIssues() {
        String[] issue1 = {"A", "Pending Triage", "0", "0", "0"};
        String[] issue2 = {"B", "Closed", "0", "0", "0", "0", "0", "0"};
        process.compareAndUpdate(issue1);
        process.compareAndUpdate(issue2);

        List<Map<String, String>> remainingIssues = process.getRemainingIssues();
        Assert.assertEquals(1, remainingIssues.size());

        Map<String, String> remainingIssue = remainingIssues.get(0);
        String[] expectedIssue = {"C", "Open", "86400", "86400", "0", "0", "0", "0"};
        Assert.assertEquals(expectedIssue, remainingIssue.values().toArray());
    }

    @Test
    public void testNewFields() {
        JiraIssuesProcess process1 = new JiraIssuesProcess();

        List<String[]> newIssues = new ArrayList<>();
        String[] newFields = {"A", "B", "C", "D", "E"};
        newIssues.add(newFields);

        String[] oldFields = {"A", "B", "C", "D"};
        process1.setNewIssues(newIssues);
        process1.setOldFields(Arrays.stream(oldFields).collect(Collectors.toList()));
        process1.checkAndAddNewFields();
        Assert.assertEquals("E", process1.getNewFields().get(0));
    }

    public void setupNewIssues() {
        String[] headers = {"issuekey", "status", "time", "issueage", "totaltime_open", "totaltime_pending_triage", "totaltime_in_progress", "totaltime_closed"};
        newIssues.add(headers);
        String[] issue1 = {"A", "In Progress", "86400", "86400", "0", "86400", "0", "0"};
        newIssues.add(issue1);
        String[] issue2 = {"C", "Open", "86400", "86400", "0", "0", "0", "0"};
        newIssues.add(issue2);
    }

}
