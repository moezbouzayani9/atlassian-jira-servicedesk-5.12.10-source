package com.atlassian.servicedesk.plugins.reports.internal;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.web.api.WebItem;
import com.atlassian.plugin.web.api.model.WebFragmentBuilder;
import com.atlassian.plugin.web.api.provider.WebItemProvider;
import com.atlassian.pocketknife.api.commons.error.AnError;
import com.atlassian.pocketknife.step.Steps;
import com.atlassian.servicedesk.internal.api.ServiceDeskServiceOld;
import com.atlassian.servicedesk.internal.api.project.InternalServiceDeskProjectManager;
import com.atlassian.servicedesk.internal.api.project.ProjectUrls;
import com.atlassian.servicedesk.internal.api.project.ProjectUrlsProvider;
import com.atlassian.servicedesk.internal.api.report.CustomReportService;
import com.atlassian.servicedesk.internal.api.report.Report;
import com.atlassian.servicedesk.internal.api.user.UserFactoryOld;
import com.atlassian.servicedesk.internal.api.util.UrlMode;
import io.atlassian.fugue.Either;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

@Component
public class CustomReportsItemProvider implements WebItemProvider {
    private JiraAuthenticationContext authenticationContext;
    private CustomReportService reportService;
    private InternalServiceDeskProjectManager internalServiceDeskProjectManager;
    private ServiceDeskServiceOld serviceDeskService;
    private ProjectUrlsProvider projectUrlProvider;
    private final UserFactoryOld userFactoryOld;

    @Autowired
    public CustomReportsItemProvider(
        JiraAuthenticationContext authenticationContext,
        CustomReportService reportService,
        InternalServiceDeskProjectManager internalServiceDeskProjectManager,
        ServiceDeskServiceOld serviceDeskService,
        ProjectUrlsProvider projectUrlProvider,
        UserFactoryOld userFactoryOld) {

        this.authenticationContext = authenticationContext;
        this.reportService = reportService;
        this.internalServiceDeskProjectManager = internalServiceDeskProjectManager;
        this.serviceDeskService = serviceDeskService;
        this.projectUrlProvider = projectUrlProvider;
        this.userFactoryOld = userFactoryOld;
    }

    @Override
    public Iterable<WebItem> getItems(Map<String, Object> context) {
        if (!context.containsKey("projectKey")) {
            return emptyList();
        }

        String projectKey = (String) context.get("projectKey");
        ApplicationUser user = authenticationContext.getLoggedInUser();

        ProjectUrls projectUrls = projectUrlProvider.getUrls(projectKey, UrlMode.RELATIVE);

        Either<AnError, List<WebItem>> result = Steps.begin(userFactoryOld.wrap(user))
            .then(checkedUser -> internalServiceDeskProjectManager.getProjectByKey(projectKey))
            .then((checkedUser, project) -> serviceDeskService.getServiceDeskForProject(user, project))
            .then((checkedUser, project, serviceDesk) -> reportService.getAllVisibleReports(checkedUser, serviceDesk))
            .yield((checkedUser, project, serviceDesk, reports) -> buildWebFragmentsFromReports(projectUrls, reports));

        return result.getOrElse(emptyList());
    }

    private List<WebItem> buildWebFragmentsFromReports(ProjectUrls projectUrls, List<Report> reports) {
        return reports.stream()
            .map(report -> new WebFragmentBuilder("com.atlassian.servicedesk:reports-nav-" + report.getId(), 0)
                .label(report.getName())
                .addParam("entityId", "" + report.getId())
                .addParam("pageId", "custom")
                .webItem("")
                .url(projectUrls.customReport(report.getId().toString()).toASCIIString())
                .build()
            )
            .collect(toList());
    }
}