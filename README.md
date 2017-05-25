# To Run Locally
1. Create a file called "jirapassword.properties". It should have two fields: jira.username.indexer and jira.password.indexer. Set those to your JIRA account. This file is added to .gitignore, so you shouldn't able to accidentally commit it.
2. Set VM Options: ```"-Dindeed.staging.level=dev -Dindeed.dc=dev -Dindeed.application=JiraActionsIndexBuilderCommandLineTool -Dindeed.instance=JiraActionsIndexBuilderCommandLineTool -Dindeed.product.group=jobsearch"```
3. Set Program Options: ```"--start <start time, for example 2016-09-21> --end <end time, for example 2016-09-22> --props <path to jirapassword.properties from part 1, for example /home/kbinswanger/indeed/jiraactions/jirapassword.properties> --jiraBatchSize <batchSize, for example 10 or 25>"```
