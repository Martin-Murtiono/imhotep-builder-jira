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
    private List<String> fields;
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

    public void setupParser() throws FileNotFoundException {
        this.file = new File("jiraissues_downloaded.tsv");
        this.reader = new FileReader(file);
        this.parser = new TsvParser(settings);
        parser.beginParsing(reader);

        fields = Arrays.stream(newIssues.get(0)).collect(Collectors.toList());
        fileWriter.setFields(fields);   // Sets fields of the TSV using new issues.

        process.setOldFields(Arrays.stream(parser.parseNext()).collect(Collectors.toList()));
        process.setNewIssues(newIssues);
        process.convertToMap();
    }

    public void parseTsv() {
        int counter = 0;
        while(true) {
            String[] issue = getIssue();
            if (issue == null) {
                break;
            } else {
                writeIssue(process.compareAndUpdate(issue));
                counter++;
            }
        }
        log.debug("Updated/Replaced {} Issues.", counter);
        for(Map<String, String> issue : process.getRemainingIssues()) {     // Adds the remaining issues
            writeIssue(issue);
        }
    }

    public String[] getIssue() {
        return parser.parseNext();
    }

    public void writeIssue(final Map<String, String> issue) {
        fileWriter.writeIssue(issue);
    }

}
