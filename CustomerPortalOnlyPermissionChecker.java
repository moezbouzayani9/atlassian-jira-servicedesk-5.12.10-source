package com.atlassian.servicedesk.internal.permission.security.type;

import com.atlassian.jira.bc.user.search.UserSearchParams;
import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.jira.config.FeatureManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.plugin.ProjectPermissionKey;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.pocketknife.api.commons.error.AnError;
import com.atlassian.servicedesk.api.ServiceDesk;
import com.atlassian.servicedesk.api.customer.CustomerContextService;
import com.atlassian.servicedesk.api.user.CheckedUser;
import com.atlassian.servicedesk.internal.api.featureflag.ServiceDeskFeatureFlags;
import com.atlassian.servicedesk.internal.api.user.UserFactoryOld;
import com.atlassian.servicedesk.internal.feature.organization.member.CustomerOrganizationMemberManager;
import com.atlassian.servicedesk.internal.feature.servicedesk.InternalServiceDeskAccessManager;
import com.atlassian.servicedesk.internal.feature.servicedesk.ServiceDeskInternalManager;
import com.atlassian.servicedesk.internal.permission.security.RequestAccessUserStrategyManager;
import com.atlassian.servicedesk.internal.user.permission.roles.ServiceDeskJiraRoleManager;
import com.atlassian.servicedesk.internal.user.permission.roles.ServiceDeskProjectRole;
import com.atlassian.servicedesk.internal.utils.context.ServiceDeskOutsideCustomerPermissionContext;
import com.google.common.collect.ImmutableList;
import io.atlassian.fugue.Either;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static io.atlassian.fugue.Suppliers.alwaysFalse;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

/**
 * Please don't use this class outside of this package.
 */
@Component
public class CustomerPortalOnlyPermissionChecker {

    private static final Logger log = LoggerFactory.getLogger(CustomerPortalOnlyPermissionChecker.class);

    private final UserFactoryOld userFactoryOld;
    private final ServiceDeskJiraRoleManager serviceDeskJIRARoleManager;
    private final ServiceDeskInternalManager serviceDeskManager;
    private final InternalServiceDeskAccessManager internalServiceDeskAccessManager;
    private final RequestAccessUserStrategyManager requestAccessUserStrategyManager;
    private final CustomerContextService customerContextService;
    private final CustomerOrganizationMemberManager customerOrganizationMemberManager;
    private final UserManager userManager;
    private final UserSearchService userSearchService;
    private final ServiceDeskOutsideCustomerPermissionContext serviceDeskOutsideCustomerPermissionContext;

    private final FeatureManager featureManager;


    //we need to have ProjectPermissionKey[] specifically for anonymous permission
    //because JIRA does not provide a way check for hasPermission with a specific project permission key
    //As a result, we need to rely on {@link ServiceDeskAnonymousPermissionOverride} to restrict
    //permission access to other type of permissions
    private static final List<ProjectPermissionKey> ANONYMOUS_PERMISSION = ImmutableList.of(
        ProjectPermissions.BROWSE_PROJECTS,
        ProjectPermissions.CREATE_ISSUES,
        ProjectPermissions.CREATE_ATTACHMENTS,
        ProjectPermissions.SCHEDULE_ISSUES
    );

    @Autowired
    public CustomerPortalOnlyPermissionChecker(
            final CustomerContextService customerContextService,
            final UserFactoryOld userFactoryOld,
            final ServiceDeskJiraRoleManager serviceDeskJIRARoleManager,
            final ServiceDeskInternalManager serviceDeskManager,
            final InternalServiceDeskAccessManager internalServiceDeskAccessManager,
            final RequestAccessUserStrategyManager requestAccessUserStrategyManager,
            final CustomerOrganizationMemberManager customerOrganizationMemberManager,
            final UserManager userManager,
            final UserSearchService userSearchService,
            final ServiceDeskOutsideCustomerPermissionContext serviceDeskOutsideCustomerPermissionContext,
            final FeatureManager featureManager) {
        this.customerContextService = customerContextService;
        this.userFactoryOld = userFactoryOld;
        this.serviceDeskJIRARoleManager = serviceDeskJIRARoleManager;
        this.serviceDeskManager = serviceDeskManager;
        this.internalServiceDeskAccessManager = internalServiceDeskAccessManager;
        this.requestAccessUserStrategyManager = requestAccessUserStrategyManager;
        this.customerOrganizationMemberManager = customerOrganizationMemberManager;
        this.userManager = userManager;
        this.userSearchService = userSearchService;
        this.serviceDeskOutsideCustomerPermissionContext = serviceDeskOutsideCustomerPermissionContext;
        this.featureManager = featureManager;
    }

    public Set<ApplicationUser> getCustomerPortalOnlyPermissionUsers(final Project project,
                                                                     final String query,
                                                                     final boolean includeInactiveUsers) {
        // for now, allow this behaviour of retrieving all users to be disabled if the feature is off! Just to be sure
        // this method is not used in any unexpected places
        if (!featureManager.isEnabled(ServiceDeskFeatureFlags.USE_SEARCH_BY_PERMISSIONS)) {
            return emptySet();
        }

        if (!customerContextService.isInCustomerContext()) {
            return emptySet();
        }

        // you would want to avoid calling this in an open access project, but here for completeness
        if (isOpenAccess(project)) {
            return getUsers(query, includeInactiveUsers);
        }

        return Stream.concat(
            serviceDeskJIRARoleManager.getAllUsersInRole(project, ServiceDeskProjectRole.CUSTOMER).stream(),
            getOrganizationMembersForProject(project)
        ).collect(toSet());
    }

    /**
     * Converts the user into a checked user and does a customer portal only check on the issue
     */
    public boolean hasCustomerPortalOnlyPermission(ApplicationUser user, Issue issue) {
        return hasCustomerPortalOnlyPermissionForIssue(user, issue);
    }

    /**
     * Converts the user into a checked user and does a customer portal only check on the project
     */
    public boolean hasCustomerPortalOnlyPermission(ApplicationUser user, Project project) {
        return hasCustomerPortalOnlyPermissionForProject(user, project);
    }

    /**
     * Check if the request is coming from custoemr portal and then check anonymous access to the project
     */
    public boolean hasCustomerPortalOnlyPermissionForAnonymous(Project project) {
        return customerContextService.isInCustomerContext() &&
            featureManager.isEnabled(ServiceDeskFeatureFlags.DELAY_LOGIN) &&
            hasAnonymousAccess(project);
    }

    private boolean hasAnonymousAccess(final Project project) {
        final Either<AnError, ServiceDesk> serviceDeskEither = serviceDeskManager.getServiceDesk(project, false);
        return serviceDeskEither.map(internalServiceDeskAccessManager::isAnonymousAccessAllowed).getOrElse(false);
    }

    public boolean isValidForAnonymousPermission(final ProjectPermissionKey permissionKey) {
        if (!featureManager.isEnabled(ServiceDeskFeatureFlags.DELAY_LOGIN)) {
            return false;
        } else {
            return ANONYMOUS_PERMISSION.stream().anyMatch(permission -> permission.equals(permissionKey));
        }
    }

    /**
     * If we are given an issue to check, we should check that the user has permission to view the issue in the portal
     * This builds the results using a combination of strategies which can be quite expensive. If you can accomplish
     * what you need with a simple permission check consider using that instead
     */
    private boolean hasCustomerPortalOnlyPermissionForIssue(ApplicationUser user, Issue issue) {
        // check project level permission first (as its quicker)
        // then issue level permission (as its slower)
        return hasCustomerPortalOnlyPermissionForProject(user, issue.getProjectObject()) && requestAccessUserStrategyManager.match(user, issue);
    }

    /**
     * This method checks the executing request to make sure we are in the correct context and then checks that given user is in the Customer Role for
     * the provided project.
     */
    private boolean hasCustomerPortalOnlyPermissionForProject(ApplicationUser user, Project project) {
        return userFactoryOld.wrap(user)
            .map(checkedUser -> customerContextService.isInCustomerContext() && allowUserToAccessPortal(checkedUser, project))
            .getOr(alwaysFalse());
    }

    private boolean allowUserToAccessPortal(CheckedUser checkedUser, Project project) {
        return isOpenAccess(project) ||
            serviceDeskJIRARoleManager.isUserInRole(checkedUser, project, ServiceDeskProjectRole.CUSTOMER) ||
            isMemberOfAnyOrganisationsInProject(checkedUser, project) ||
            serviceDeskOutsideCustomerPermissionContext.isInProjectOutsideCustomerContext(checkedUser, project);
    }

    private boolean isOpenAccess(Project project) {
        Either<AnError, ServiceDesk> serviceDeskEither = serviceDeskManager.getServiceDesk(project, false);
        return serviceDeskEither.exists(internalServiceDeskAccessManager::isOpenAccess);
    }

    private boolean isMemberOfAnyOrganisationsInProject(CheckedUser checkedUser, Project project) {
        return customerOrganizationMemberManager.isMemberOfAnyOrganizationsInProject(checkedUser, project);
    }

    @Nonnull
    private Set<ApplicationUser> getUsers(@Nonnull final String query, final boolean includeInactiveUsers) {
        if (query.isEmpty()) {
            log.warn("This call is going to return all users on your instance. Better to check if service project is open access first, and avoid this call. Use debug logging to see this call stack");
            log.debug("Current call stack that is asking to return all users on your instance", new Exception());

            if (includeInactiveUsers) {
                return new HashSet<>(userManager.getAllApplicationUsers());
            }
        }

        final UserSearchParams allUsersSearchParams = UserSearchParams.builder()
                .allowEmptyQuery(true)
                .canMatchEmail(false)
                .forceStrongConsistency(true)
                .includeActive(true)
                .includeInactive(includeInactiveUsers)
                .build();

        return new LinkedHashSet<>(userSearchService.findUsers(query, allUsersSearchParams));
    }

    private Stream<ApplicationUser> getOrganizationMembersForProject(Project project) {
        return customerOrganizationMemberManager.getOrganizationMembersForProject(project).stream();
    }
}
