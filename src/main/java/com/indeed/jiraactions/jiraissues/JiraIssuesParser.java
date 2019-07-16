package com.indeed.jiraactions.jiraissues;

import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JiraIssuesParser {
    private static final Logger log = LoggerFactory.getLogger(JiraIssuesIndexBuilder.class);
    private static TsvParserSettings settings = setupSettings(new TsvParserSettings());

    private final JiraIssuesProcess process;
    private final JiraIssuesFileWriter fileWriter;

    private List<String[]> newIssues;
    private File file;
    private FileReader reader;
    private TsvParser parser;

    public JiraIssuesParser(final JiraIssuesFileWriter fileWriter, final JiraIssuesProcess process, final List<String[]> newIssues) {
        this.fileWriter = fileWriter;
        this.process = process;
        this.newIssues = newIssues;
    }

    private static TsvParserSettings setupSettings(TsvParserSettings settings) {
        settings.getFormat().setLineSeparator("\n");
        settings.setMaxColumns(1000);
        settings.setMaxCharsPerColumn(10000);
        settings.setNullValue("");
        return settings;
    }

    public void setupParserAndProcess() throws FileNotFoundException {
        file = new File("jiraissues_downloaded.tsv");
        reader = new FileReader(file);
        parser = new TsvParser(settings);
        parser.beginParsing(reader);

        fileWriter.setFields(Arrays.stream(newIssues.get(0)).collect(Collectors.toList()));   // Sets fields of the TSV using new issues.

        process.setFields(Arrays.stream(newIssues.get(0)).collect(Collectors.toList()));
        process.setNewIssues(newIssues);
        process.setOldFields(Arrays.stream(parser.parseNext()).collect(Collectors.toList()));
        process.convertToMap();
    }

    public void parseTsv() {
        int counter = 0;
        while(true) {
            String[] issue = parser.parseNext();
            if (issue == null) {
                break;
            } else {
                fileWriter.writeIssue(process.compareAndUpdate(issue));
                counter++;
            }
        }
        log.debug("Updated/Replaced {} Issues.", counter);
        for(Map<String, String> issue : process.getRemainingIssues()) {     // Adds the remaining issues
            fileWriter.writeIssue(issue);
        }
    }
}
