package com.indeed.jiraactions.api.customfields;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeed.jiraactions.FriendlyUserLookupService;
import com.indeed.jiraactions.UserLookupService;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;

public class TestCustomFieldValue {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    final UserLookupService userLookupService = new FriendlyUserLookupService();
    final CustomFieldApiParser apiParser = new CustomFieldApiParser(userLookupService);

    private static final CustomFieldDefinition directCause = ImmutableCustomFieldDefinition.builder()
            .name("Direct Cause")
            .customFieldId("customfield_17490")
            .imhotepFieldName("evnt_directcause")
            .multiValueFieldConfiguration(CustomFieldDefinition.MultiValueFieldConfiguration.EXPANDED)
            .transformation(CustomFieldDefinition.Transformation.NONE)
            .build();

    private static final CustomFieldDefinition storyPoints = ImmutableCustomFieldDefinition.builder()
            .name("Story Points")
            .customFieldId("customfield_12090")
            .imhotepFieldName("millistorypoints")
            .multiValueFieldConfiguration(CustomFieldDefinition.MultiValueFieldConfiguration.NONE)
            .transformation(CustomFieldDefinition.Transformation.MULTIPLY_BY_THOUSAND)
            .build();

    private static final CustomFieldDefinition sysadCategories = ImmutableCustomFieldDefinition.builder()
            .name("Sysad Categories")
            .customFieldId("customfield_17591")
            .imhotepFieldName("sysad_category")
            .multiValueFieldConfiguration(CustomFieldDefinition.MultiValueFieldConfiguration.SEPARATE)
            .transformation(CustomFieldDefinition.Transformation.NONE)
            .build();

    private static final CustomFieldDefinition protestCountries = ImmutableCustomFieldDefinition.builder()
            .name("Test Countries")
            .customFieldId("customfield_15290")
            .imhotepFieldName("protest_countries*|")
            .separator("|")
            .build();

    private static final CustomFieldDefinition sprint = ImmutableCustomFieldDefinition.builder()
            .name("Sprint")
            .customFieldId("customfield_11490")
            .imhotepFieldName("sprints*|")
            .separator("|")
            .build();

    private static final CustomFieldDefinition verifier = ImmutableCustomFieldDefinition.builder()
            .name("Verifier")
            .customFieldId("customfield_10003")
            .imhotepFieldName("verifier")
            .multiValueFieldConfiguration(CustomFieldDefinition.MultiValueFieldConfiguration.USERNAME)
            .build();

    private static final CustomFieldDefinition feedid = ImmutableCustomFieldDefinition.builder()
            .name("Feed ID")
            .customFieldId("customfield_10042")
            .imhotepFieldName("allfeedids*|")
            .separator("|")
            .split(CustomFieldDefinition.SplitRule.NON_NUMBER)
            .build();

    @Test
    public void testNoModifications() throws IOException {
        final CustomFieldDefinition definition = ImmutableCustomFieldDefinition.builder()
                .customFieldId("customFieldId")
                .imhotepFieldName("imhotepFieldName")
                .name("name")
                .build();
        final String value = "TestNoTransformationsValue";
        final CustomFieldValue field = new CustomFieldValue(definition, value);

        assertEquals(field, value);
    }

    @Test
    public void testMultiplyByThousand() throws IOException {
        testFromInitial(storyPoints, "1.1", "1100");
    }

    @Test
    public void testTextFromInitial() throws IOException {
        testFromInitial(storyPoints, "8.0", "8000");
    }

    @Test
    public void testValueFromInitial() throws IOException {
        testFromInitial(protestCountries, "{\"self\":\"https://jira.example.com/rest/api/2/customFieldOption/12062\",\"value\":\"User\",\"id\":\"12062\"}", "User");
    }

    @Test
    public void testSingleValueArrayOfValuesFromInitial() throws IOException {
        testFromInitial(protestCountries, "[{\"self\":\"https://jira.example.com/rest/api/2/customFieldOption/16473\",\"value\":\"en only\",\"id\":\"16473\"}]", "en only");
    }

    @Test
    public void testArrayOfValuesFromInitialWithoutSeparator() throws IOException {
        final CustomFieldDefinition madeup = ImmutableCustomFieldDefinition.builder()
                .name("Fake Test Countries")
                .customFieldId("customfield_15290")
                .imhotepFieldName("fake_protest_countries*")
                .build();

        testFromInitial(madeup, "[{\"self\":\"https://jira.example.com/rest/api/2/customFieldOption/16473\",\"value\":\"en_only\",\"id\":\"16473\"},{\"self\":\"https://jira.example.com/rest/api/2/customFieldOption/16475\",\"value\":\"worldwide\",\"id\":\"16475\"}]",
                "en_only worldwide");
    }

    @Test
    public void testArrayOfValuesFromInitialWithSeparator() throws IOException {
        testFromInitial(protestCountries, "[{\"self\":\"https://jira.example.com/rest/api/2/customFieldOption/16473\",\"value\":\"en only\",\"id\":\"16473\"},{\"self\":\"https://jira.example.com/rest/api/2/customFieldOption/16475\",\"value\":\"worldwide\",\"id\":\"16475\"}]",
                "en only|worldwide");
    }

    @Test
    public void testArrayOfTextFromInitial() throws IOException {
        final CustomFieldDefinition madeup = ImmutableCustomFieldDefinition.builder()
                .name("Labels")
                .customFieldId("customfield_00000")
                .imhotepFieldName("labels*")
                .build();

        testFromInitial(madeup, "[\"fixit\",\"jobsearch-library-update\",\"jsgrowth\"]", "fixit jobsearch-library-update jsgrowth");
    }

    @Test
    public void testExpandedWithChildFromInitial() throws IOException {
        testFromInitial(directCause, "{\"self\":\"https://jira.example.com/rest/api/2/customFieldOption/20661\",\"value\":\"Misconfiguration\",\"id\":\"20661\",\"child\":{\"self\":\"https://jira.example.com/rest/api/2/customFieldOption/20669\",\"value\":\"App Config\",\"id\":\"20669\"}}",
                "Misconfiguration - App Config");
    }

    @Test
    public void testExpandedNoChildFromInitial() throws IOException {
        testFromInitial(directCause, "{\"self\":\"https://jira.example.com/rest/api/2/customFieldOption/20661\",\"value\":\"Misconfiguration\",\"id\":\"20661\"}", "Misconfiguration");
    }

    @Test
    public void testSeparateWithChildFromInitial() throws IOException {
        testFromInitial(sysadCategories, "{\"self\":\"https://jira.example.com/rest/api/2/customFieldOption/20781\",\"value\":\"Config\",\"id\":\"20781\",\"child\":{\"self\":\"https://jira.example.com/rest/api/2/customFieldOption/20814\",\"value\":\"Other\",\"id\":\"20814\"}}",
                "Config\tOther\t");
    }

    @Test
    public void testSeparateWithoutChildFromInitial() throws IOException {
        testFromInitial(sysadCategories, "{\"self\":\"https://jira.example.com/rest/api/2/customFieldOption/20787\",\"value\":\"DNS\",\"id\":\"20787\"}", "DNS\t\t");
    }

    @Test
    public void testGreenhopperObjectFromInitial() throws IOException {
        testFromInitial(sprint, "[\"com.atlassian.greenhopper.service.sprint.Sprint@8b01a9a[id=811,rapidViewId=1712,state=CLOSED,name=WP Sprint 2,startDate=2017-08-07T14:30:48.610-05:00,endDate=2017-08-21T14:30:00.000-05:00,completeDate=2017-08-21T13:34:01.341-05:00,sequence=811]\",\"com.atlassian.greenhopper.service.sprint.Sprint@8a8d1d7[id=842,rapidViewId=1712,state=CLOSED,name=Sprint R: 8/21-9/1,startDate=2017-08-21T14:05:47.318-05:00,endDate=2017-09-04T14:05:00.000-05:00,completeDate=2017-09-05T10:06:40.253-05:00,sequence=842]\"]",
                "WP Sprint 2|Sprint R: 8/21-9/1");
    }

    @Test
    public void testUserLookupFromInitial() throws IOException {
        testFromInitial(verifier, "{\"self\":\"https://jira.example.com/rest/api/2/user?username=johndoe\",\"name\":\"johndoe\",\"key\":\"johndoe\",\"emailAddress\":\"johndoe@example.com\",\"avatarUrls\":{\"48x48\":\"https://jira.example.com/secure/useravatar?ownerId=johndoe&avatarId=25105\",\"24x24\":\"https://jira.example.com/secure/useravatar?size=small&ownerId=johndoe&avatarId=25105\",\"16x16\":\"https://jira.example.com/secure/useravatar?size=xsmall&ownerId=johndoe&avatarId=25105\",\"32x32\":\"https://jira.example.com/secure/useravatar?size=medium&ownerId=johndoe&avatarId=25105\"},\"displayName\":\"John Doe\",\"active\":true,\"timeZone\":\"America/Chicago\"}{\"self\":\"https://jira.example.com/rest/api/2/user?username=johndoe\",\"name\":\"johndoe\",\"key\":\"johndoe\",\"emailAddress\":\"johndoe@example.com\",\"avatarUrls\":{\"48x48\":\"https://jira.example.com/secure/useravatar?ownerId=johndoe&avatarId=25105\",\"24x24\":\"https://jira.example.com/secure/useravatar?size=small&ownerId=johndoe&avatarId=25105\",\"16x16\":\"https://jira.example.com/secure/useravatar?size=xsmall&ownerId=johndoe&avatarId=25105\",\"32x32\":\"https://jira.example.com/secure/useravatar?size=medium&ownerId=johndoe&avatarId=25105\"},\"displayName\":\"John Doe\",\"active\":true,\"timeZone\":\"America/Chicago\"}",
                "John Doe\tjohndoe\t");
    }

    @Test
    public void testSplitFromInitial() throws IOException {
        testFromInitial(feedid, "\"25448, 144772, 10150, 260152, 72045, 186255, 72022, 120551, 75954, 136235, 71965, 72048, 72044, 70836, 15049, 72536, 13876, 70863, 44076, 64467, 72047, 59019, 187277, 59234, 148076, 7016, 14482, 40794, 72050, 213443, 68955, 7662, 21344, 69180\"",
                "25448|144772|10150|260152|72045|186255|72022|120551|75954|136235|71965|72048|72044|70836|15049|72536|13876|70863|44076|64467|72047|59019|187277|59234|148076|7016|14482|40794|72050|213443|68955|7662|21344|69180");
    }

    @Test
    public void testSplitFromChangelog() throws IOException {
        final String value = "25448, 144772, 10150, 260152, 186255, 70836, 15049, 72536, 13876, 70863, 44076, 64467, 72047, 59019, 59234, 148076, 7016, 14482, 40794, 72050, 213443, 68955";
        final CustomFieldValue field = apiParser.customFieldValueFromChangelog(feedid, "", value);

        assertEquals(field, "25448|144772|10150|260152|186255|70836|15049|72536|13876|70863|44076|64467|72047|59019|59234|148076|7016|14482|40794|72050|213443|68955");
    }

    @Test
    public void testExpandedWithChildFromChangelog() throws IOException {
        final String value = "Parent values: Escaped bug(20664)Level 1 values: Latent Code Issue(20681)";
        final CustomFieldValue field = apiParser.customFieldValueFromChangelog(directCause, "", value);

        assertEquals(field, "Escaped bug - Latent Code Issue");
    }

    @Test
    public void testExpandedWithoutChildFromChangelog() throws IOException {
        final String value = "Parent values: Escaped bug(20664)";
        final CustomFieldValue field = apiParser.customFieldValueFromChangelog(directCause, "", value);

        assertEquals(field, "Escaped bug");
    }

    @Test
    public void testSeparateWithChildFromChangelog() throws IOException {
        final String value = "Parent values: Misconfiguration(20661)Level 1 values: App Config(20669)";
        final CustomFieldValue field = apiParser.customFieldValueFromChangelog(sysadCategories, "", value);

        assertEquals(field, "Misconfiguration\tApp Config\t");
    }

    @Test
    public void testSeparateWithoutChildFromChangelog() throws IOException {
        final String value = "Parent values: Misconfiguration(20661)";
        final CustomFieldValue field = apiParser.customFieldValueFromChangelog(sysadCategories, "", value);

        // These tabs are important because we need the empty space for the fields that aren't present
        assertEquals(field, "Misconfiguration\t\t");
    }

    @Test
    public void testUserLookupFromChangelog() throws IOException {
        final CustomFieldValue field = apiParser.customFieldValueFromChangelog(verifier, "aaldridge", "Andreas Aldridge");
        assertEquals(field, "Andreas Aldridge\taaldridge\t");
    }

    @Test
    public void testSeparateAndTransformedFromChangelog() throws IOException {
        final CustomFieldDefinition definition = ImmutableCustomFieldDefinition.builder()
                .name("Made Up")
                .customFieldId("customfield_00000")
                .imhotepFieldName("fieldName")
                .multiValueFieldConfiguration(CustomFieldDefinition.MultiValueFieldConfiguration.SEPARATE)
                .transformation(CustomFieldDefinition.Transformation.MULTIPLY_BY_THOUSAND)
                .build();
        final String value = "Parent values: 99(32767)Level 1 values: .5(86753)";
        final CustomFieldValue field = apiParser.customFieldValueFromChangelog(definition, "", value);

        assertEquals(field, "99000\t500\t");
    }

    @Test
    public void testExpandedAndTransformedFromChangelog() throws IOException {
        final CustomFieldDefinition definition = ImmutableCustomFieldDefinition.builder()
                .name("Made Up")
                .customFieldId("customfield_00000")
                .imhotepFieldName("fieldName")
                .multiValueFieldConfiguration(CustomFieldDefinition.MultiValueFieldConfiguration.EXPANDED)
                .transformation(CustomFieldDefinition.Transformation.MULTIPLY_BY_THOUSAND)
                .build();
        final String value = "Parent values: 9.9(32767)Level 1 values: .5(86753)";
        final CustomFieldValue field = apiParser.customFieldValueFromChangelog(definition, "", value);

        assertEquals(field, "9900 - 500");
    }

    @Test
    public void testMultiValueFIeldFromChangelog() throws IOException {
        final String value = "2016-11-02 Money, 2016-11-09 Money, 2016-11-16 Money, 2016-11-23 Money, 2016-11-30 Money, 2016-12-07 Money, 2016-12-14 Money";
        final CustomFieldValue field = apiParser.customFieldValueFromChangelog(sprint, "", value);

        assertEquals(field, "2016-11-02 Money|2016-11-09 Money|2016-11-16 Money|2016-11-23 Money|2016-11-30 Money|2016-12-07 Money|2016-12-14 Money");
    }

    @Test
    public void testNumericStringToMilliNumericString() {
        Assert.assertEquals("", CustomFieldValue.numericStringToMilliNumericString(null));
        Assert.assertEquals("", CustomFieldValue.numericStringToMilliNumericString(""));
        Assert.assertEquals("", CustomFieldValue.numericStringToMilliNumericString("5 cows"));

        Assert.assertEquals("5000", CustomFieldValue.numericStringToMilliNumericString("5"));
        Assert.assertEquals("230", CustomFieldValue.numericStringToMilliNumericString(".23"));
    }

    private void testFromInitial(final CustomFieldDefinition definition, final String input, final String expected) throws IOException {
        final JsonNode node = OBJECT_MAPPER.readTree(input);
        final CustomFieldValue field = apiParser.customFieldFromInitialFields(definition, node);

        assertEquals(field, expected);
    }

    private void assertEquals(final CustomFieldValue field, final String expected) throws IOException {
        final StringWriter writer = new StringWriter();

        Assert.assertEquals(expected, String.join("\t", field.getValues()));
    }
}
