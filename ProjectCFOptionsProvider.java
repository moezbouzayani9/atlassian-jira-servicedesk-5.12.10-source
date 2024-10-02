package com.atlassian.servicedesk.internal.fields.optionsprovider;

import com.atlassian.jira.bc.ServiceOutcome;
import com.atlassian.jira.bc.project.ProjectService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.customfields.impl.ProjectCFType;
import com.atlassian.jira.issue.fields.Field;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.servicedesk.api.customer.CustomerContextService;
import com.atlassian.servicedesk.internal.api.customfields.OptionsProvider;
import com.atlassian.servicedesk.internal.api.rest.responses.JiraFieldValue;
import java.util.List;

import static com.atlassian.servicedesk.internal.fields.JIRAFieldsUtil.checkAndReturnCFType;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public class ProjectCFOptionsProvider implements OptionsProvider {

    private final ProjectService projectService;
    private final JiraAuthenticationContext jiraAuthenticationContext;
    private final CustomerContextService customerContextService;

    public ProjectCFOptionsProvider(final ProjectService projectService,
                                    final JiraAuthenticationContext jiraAuthenticationContext,
                                    final CustomerContextService customerContextService) {
        this.projectService = projectService;
        this.jiraAuthenticationContext = jiraAuthenticationContext;
        this.customerContextService = customerContextService;
    }

    @Override
    public boolean accept(final Field jiraField) {
        return checkAndReturnCFType(jiraField, ProjectCFType.class).isDefined();
    }

    @Override
    public List<JiraFieldValue> apply(final Field jiraField, final Issue issueCtx) {
        //Running out of customer context as customer context will give access to all open JSD projects
        final ServiceOutcome<List<Project>> projectsOutcome = customerContextService.runOutOfCustomerContext(() ->
            projectService.getAllProjects(jiraAuthenticationContext.getLoggedInUser()));

        if (projectsOutcome.isValid()) {
            return projectsOutcome.getReturnedValue().stream()
                .map(level -> new JiraFieldValue(
                    String.valueOf(level.getId()),
                    level.getName(),
                    false
                ))
                .collect(toList());
        } else {
            return emptyList();
        }
    }
}
