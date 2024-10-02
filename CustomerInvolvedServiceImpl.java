package com.atlassian.servicedesk.internal.permission.security;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.project.Project;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.servicedesk.api.customer.CustomerContextService;
import com.atlassian.servicedesk.api.user.CheckedUser;
import com.atlassian.servicedesk.internal.api.permission.security.CustomerInvolvedService;
import com.atlassian.servicedesk.internal.api.user.permission.ServiceDeskLicenseAndPermissionService;
import com.atlassian.servicedesk.internal.spi.permission.security.CustomerInvolvedType;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static java.util.stream.Collectors.toList;

@Component
@ExportAsService
public class CustomerInvolvedServiceImpl implements CustomerInvolvedService {
    private final ServiceDeskLicenseAndPermissionService serviceDeskLicenseAndPermissionService;
    private final CustomerContextService customerContextService;
    private final RequestAccessUserStrategyManager requestAccessUserStrategyManager;

    @Autowired
    public CustomerInvolvedServiceImpl(ServiceDeskLicenseAndPermissionService serviceDeskLicenseAndPermissionService,
                                       CustomerContextService customerContextService,
                                       RequestAccessUserStrategyManager requestAccessUserStrategyManager) {
        this.serviceDeskLicenseAndPermissionService = serviceDeskLicenseAndPermissionService;
        this.customerContextService = customerContextService;
        this.requestAccessUserStrategyManager = requestAccessUserStrategyManager;
    }

    @Override
    public List<CheckedUser> getMembers(Issue issue) {
        final List<CheckedUser> members = requestAccessUserStrategyManager.getMembers(issue);
        return filterPermissions(issue, members);
    }

    @Override
    public List<CheckedUser> getMembersForTypes(final Issue issue, final CustomerInvolvedType... types) {
        return filterPermissions(issue, getMembersForTypesNoPermissionChecks(issue, types));
    }

    @Override
    public List<CheckedUser> getMembersForTypesNoPermissionChecks(final Issue issue,
                                                                  final CustomerInvolvedType... types) {
        return requestAccessUserStrategyManager.getMembersForTypes(issue, types);
    }

    private List<CheckedUser> filterPermissions(Issue issue, List<CheckedUser> users) {
        final List<CheckedUser> checkedPermissionsUsers =
            users.stream().filter(user -> hasAccessToRequest(user, issue)).collect(toList());
        return ImmutableList.copyOf(checkedPermissionsUsers);
    }

    /**
     * Basically, check browse and create request permissions
     */
    @Override
    public boolean hasAccessToRequest(final CheckedUser user, final Issue issue) {
        final Project project = issue.getProjectObject();
        if (project == null) {
            return false;
        }

        return customerContextService
            .runInCustomerContext(() ->
                (serviceDeskLicenseAndPermissionService.canViewPortalPermissionFixTransition(user, project)
                    || serviceDeskLicenseAndPermissionService.userIsAgentOfIssue(user, issue)) // should not be relevant for CustomerInvolvedService
                    && requestAccessUserStrategyManager.match(user.forJIRA(), issue));
    }

    @Override
    public boolean isUserOfType(final CheckedUser user, final Issue issue, final CustomerInvolvedType... types) {
        final Project project = issue.getProjectObject();
        if (project == null) {
            return false;
        }

        return customerContextService
            .runInCustomerContext(() -> requestAccessUserStrategyManager.match(user.forJIRA(), issue, types)
                && (serviceDeskLicenseAndPermissionService.canViewPortalPermissionFixTransition(user, project)
                || serviceDeskLicenseAndPermissionService.userIsAgentOfIssue(user, issue))); // should not be relevant for CustomerInvolvedService
    }
}
