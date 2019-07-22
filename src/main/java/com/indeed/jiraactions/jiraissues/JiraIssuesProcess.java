package com.indeed.jiraactions.jiraissues;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class JiraIssuesProcess {
    private static final Logger log = LoggerFactory.getLogger(JiraIssuesIndexBuilder.class);

    private final List<Map<String, String>> newIssuesMapped = new ArrayList<>();
    private final List<String> nonApiStatuses = new ArrayList<>(); // Old statuses that don't show up in the API.
    private final List<String> newFields = new ArrayList<>(); // New fields in updatedIssues not in previous TSV.
    private List<String[]> newIssues;
    private List<String> fields; // Fields from updatedIssues.
    private List<String> oldFields; // Fields from previous TSV

    public JiraIssuesProcess() {}

    public void convertToMap() {
        for(int issue = 1; issue < newIssues.size(); issue++) {
            final Map<String, String> mappedLine = new LinkedHashMap<>(fields.size());
            final String[] line = newIssues.get(issue);
            if(line == null) {
                break;
            } else {
                for(int i = 0; i < line.length; i++) {
                    mappedLine.put(fields.get(i), line[i]);
                }
                newIssuesMapped.add(mappedLine);
            }
        }
    }

    public void checkAndAddNewFields(){
        if(oldFields.size() != fields.size()) {
            for(int i = 0; i < fields.size(); i++) {
                if(!oldFields.contains(fields.get(i))) {
                    newFields.add(fields.get(i));
                }
            }
            log.debug("New fields: {}", String.join(" ", newFields));
        } else {
            log.info("No new fields.");
        }
    }

    /* If the issue is updated through jiraactions it will replace it because that version is the latest.
     * If it isn't replaced then it gets updated -- only fields involving time are updated so this is really easy.
     * Issues from jiraactions are removed when they get replaced meaning that the ones remaining are new issues and are therefore added.
     */
    public Map<String, String> compareAndUpdate(final String[] issue) {
        final Map<String, String> mappedLine = new LinkedHashMap<>();
        // Changes the issue from a String[] to a Map<String, String>
        for(int i = 0; i < issue.length; i++) {
            mappedLine.put(oldFields.get(i), issue[i]);
        }

        for (Map<String, String> updatedIssue: newIssuesMapped) {
            if (mappedLine.get("issuekey").equals(updatedIssue.get("issuekey"))) {
                newIssuesMapped.remove(updatedIssue);
                return updatedIssue;  // Replace
            }
        }
            return updateIssue(mappedLine);   // Update
    }

    public List<Map<String, String>> getRemainingIssues() {
        final List<Map<String, String>> addedIssues = new ArrayList<>();
        if (!newIssues.isEmpty()) {
            addedIssues.addAll(newIssuesMapped);
            log.debug("Added {} new issues.", addedIssues.size());
        }
        return addedIssues;
    }

    public Map<String, String> updateIssue(final Map<String, String> mappedLine) {
        final long DAY = TimeUnit.DAYS.toSeconds(1);
        final String status = formatStatus(mappedLine.get("status"));
        try {
            mappedLine.replace("issueage", mappedLine.get("issueage"), String.valueOf(Long.parseLong(mappedLine.get("issueage")) + DAY));
            mappedLine.replace("time", mappedLine.get("time"), String.valueOf(Long.parseLong(mappedLine.get("time")) + DAY));

            if(!mappedLine.containsKey("totaltime_" + status)) {
                nonApiStatuses.add(mappedLine.get("status"));
            } else {
                mappedLine.replace("totaltime_" + status, mappedLine.get("totaltime_" + status), String.valueOf(Long.parseLong(mappedLine.get("totaltime_" + status)) + DAY));
            }

        } catch (final NumberFormatException e) {
            log.error("Value of field is not numeric.", e);
        }
        if(!newFields.isEmpty()) {
            final Map<String, String> mappedLineNewFields = new LinkedHashMap<>();
            for(String field : fields) {
                if(!mappedLine.containsKey(field)) {
                    mappedLineNewFields.put(field, "0");
                } else {
                    mappedLineNewFields.put(field, mappedLine.get(field));
                }
            }
            return mappedLineNewFields;
        }
        return mappedLine;
    }

    private String formatStatus(String status) {
        if (status.equals("")) {
            return "";
        }
        return status
                .toLowerCase()
                .replace(" ", "_")
                .replace("-", "_")
                .replace("(", "")
                .replace(")", "")
                .replace("&", "and")
                .replace("/", "_");
    }

    public void setNewIssues(final List<String[]> newIssues) {
        this.newIssues = newIssues;
    }

    public void setOldFields(final List<String> oldFields) {
        this.oldFields = oldFields;
    }

    public List<String> getNewFields() {
        return newFields;
    }

    public List<String> getNonApiStatuses() {
        return nonApiStatuses;
    }

    public void setFields(final List<String> fields) {
        this.fields = fields;
    }
}
