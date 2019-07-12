package com.indeed.jiraactions.jiraissues;

import com.indeed.jiraactions.JiraActionsIndexBuilderConfig;
import com.indeed.jiraactions.JiraActionsUtil;
import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JiraIssuesParser {
    private static final Logger log = LoggerFactory.getLogger(JiraIssuesIndexBuilder.class);
    private static TsvParserSettings settings = setupSettings(new TsvParserSettings());

    private final JiraActionsIndexBuilderConfig config;
    private final JiraIssuesFileWriter fileWriter;

    private final List<String[]> newIssues;
    private List<Map<String, String>> newIssuesMapped = new ArrayList<>();
    public List<String> fields = new ArrayList<>();
    private List<String> newFields = new ArrayList<>();

    public JiraIssuesParser(final JiraActionsIndexBuilderConfig config, final JiraIssuesFileWriter fileWriter, final List<String[]> issues) {
        this.config = config;
        this.fileWriter = fileWriter;
        this.newIssues = issues;
    }

    private static TsvParserSettings setupSettings(TsvParserSettings settings) {
        settings.getFormat().setLineSeparator("\n");
        settings.setMaxColumns(1000);
        settings.setMaxCharsPerColumn(10000);
        settings.setNullValue("");
        return settings;
    }

    public void parseJiraActionsAndMakeTsv() throws Exception {
        fields = Arrays.stream(newIssues.get(0)).collect(Collectors.toList());

        for(int issue = 1; issue<newIssues.size(); issue++) {
            final Map<String, String> mappedLine = new LinkedHashMap<>(fields.size());
            String[] line = newIssues.get(issue);
            if(line == null) {
                break;
            } else {
                for(int i = 0; i < line.length; i++) {
                    mappedLine.put(fields.get(i), line[i]);
                }
                newIssuesMapped.add(mappedLine);
            }
        }
        fileWriter.createTsv(JiraActionsUtil.parseDateTime(config.getStartDate()), fields);
    }

    public void parsePreviousTsvAndUpdate() throws Exception {
        final File file = new File("jiraissues_downloaded.tsv");
        final FileReader reader = new FileReader(file);
        final TsvParser parser = new TsvParser(settings);

        parser.beginParsing(reader);
        final List<String> oldFields = Arrays.stream(parser.parseNext()).collect(Collectors.toList()); // skip fields

        // Checks if there are any new fields, such as when a new status appears in the API.
        if(oldFields.size() != fields.size()) {
            for(int i = 0; i < fields.size(); i++) {
                if(!oldFields.contains(fields.get(i))) {
                    newFields.add(fields.get(i));
                }
            }
            fileWriter.setNewFields(newFields);
            log.debug("New fields: {}", String.join(" ", newFields));
        } else {
            log.info("No new fields.");
        }


        /** If the issue is updated through jiraactions it will replace it because that version is the latest.
         * If it isn't replaced then it gets updated -- only fields involving time are updated so this is really easy.
         * Issues from jiraactions are removed when they get replaced meaning that the ones remaining are new issues and are therefore added. */
        int updateCount = 0;
        int replaceCount = 0;
        while (true) {
            boolean replaced = false;
            final Map<String, String> mappedLine = new LinkedHashMap<>();
            String[] line = parser.parseNext();
            if (line == null) {
                break;
            } else {
                // Changes the issue from a String[] to a Map<String, String>
                for(int i = 0; i < line.length; i++) {
                    mappedLine.put(oldFields.get(i), line[i]);
                }

                for (Map<String, String> issue: newIssuesMapped) {
                    if (mappedLine.get("issuekey").equals(issue.get("issuekey"))) {
                        replaceCount++;
                        fileWriter.writeIssue(issue);  // Replace
                        replaced = true;
                        newIssues.remove(issue);
                        break;
                    }
                }
                if (!replaced) {
                    updateCount++;
                    fileWriter.writeIssue(fileWriter.updateIssue(mappedLine));   // Update
                }
            }
        }
        log.debug("Updated {}, Replaced {}, issues.", updateCount, replaceCount);
        int added = 0;
        if (!newIssues.isEmpty()) {
            for (Map<String, String> issue: newIssuesMapped) {
                fileWriter.writeIssue(issue);  // Add new
                added++;
            }
            log.debug("Added {} new issues.", added);
            log.debug("Statuses not in API: {}", fileWriter.getNonApiStatuses().toArray());
        }
    }
}
