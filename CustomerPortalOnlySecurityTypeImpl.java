package com.atlassian.servicedesk.internal.permission.security.type;

import com.atlassian.crowd.embedded.impl.IdentifierUtils;
import com.atlassian.jira.bc.JiraServiceContext;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.permission.PermissionContext;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.plugin.ProjectPermissionKey;
import com.atlassian.jira.security.type.AbstractProjectsSecurityType;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.I18nHelper;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.atlassian.jira.bc.user.search.UserSearchUtilities.userSearchMatchUser;
import static java.util.Collections.emptySet;
import static java.util.Comparator.comparing;

/**
 * This is a service project implementation of the Security Type that suits our particular need for the customer view. This method mostly contains
 * wiring, the actual work happens in the CustomerPortalOnlyPermissionChecker
 */
public class CustomerPortalOnlySecurityTypeImpl
    extends AbstractProjectsSecurityType implements CustomerPortalOnlySecurityType {

    private static final Logger log = LoggerFactory.getLogger(CustomerPortalOnlySecurityTypeImpl.class);
    private static final String DISPLAY_NAME = "sd.permission.security.type.display";
    private static final String EMPTY_QUERY = "";
    // These fields are the only ones that users are able to add the CustomerPortalOnlySecurityType to.
    private static final ProjectPermissionKey[] RELEVANT_PERMISSIONS = new ProjectPermissionKey[]{
        ProjectPermissions.BROWSE_PROJECTS,
        ProjectPermissions.CREATE_ISSUES,
        ProjectPermissions.ADD_COMMENTS,
        ProjectPermissions.CREATE_ATTACHMENTS,
        ProjectPermissions.TRANSITION_ISSUES,
        ProjectPermissions.ASSIGN_ISSUES, // so the assignee field can be pre-set in the form
        ProjectPermissions.LINK_ISSUES, // so default issue links can be pre-set in the form
        ProjectPermissions.SCHEDULE_ISSUES, // for setting due date
        ProjectPermissions.SET_ISSUE_SECURITY, // so the issue security field can be pre-set in the form
        ProjectPermissions.EDIT_ISSUES,     // required so that participants can be added (edits the custom field)
        // Everything below this line is just future proofing. We don't actually need to grant these to people in the Customer role
        ProjectPermissions.MOVE_ISSUES,
        ProjectPermissions.RESOLVE_ISSUES,
        ProjectPermissions.CLOSE_ISSUES,
        ProjectPermissions.MODIFY_REPORTER,
        ProjectPermissions.DELETE_ISSUES,
        ProjectPermissions.VIEW_VOTERS_AND_WATCHERS,
        ProjectPermissions.MANAGE_WATCHERS,
        ProjectPermissions.EDIT_OWN_COMMENTS,
        ProjectPermissions.DELETE_OWN_COMMENTS,
        ProjectPermissions.DELETE_OWN_ATTACHMENTS
    };

    private final I18nHelper i18nHelper;
    private final CustomerPortalOnlyPermissionChecker customerPortalOnlyPermissionChecker;

    public CustomerPortalOnlySecurityTypeImpl(I18nHelper i18nHelper,
                                              CustomerPortalOnlyPermissionChecker customerPortalOnlyPermissionChecker) {
        this.i18nHelper = i18nHelper;
        this.customerPortalOnlyPermissionChecker = customerPortalOnlyPermissionChecker;
    }

    @Override
    public String getDisplayName() {
        return i18nHelper.getText(DISPLAY_NAME);
    }

    @Override
    public String getType() {
        return CustomerPortalOnlySecurityType.TYPE;
    }

    /**
     * This checks if the permission we are checking will ever be valid.
     */
    @Override
    public boolean isValidForPermission(ProjectPermissionKey permissionKey) {
        for (ProjectPermissionKey permission : RELEVANT_PERMISSIONS) {
            if (permission.equals(permissionKey)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasPermission(Project project, String parameter, ApplicationUser user, boolean issueCreation) {
        return customerPortalOnlyPermissionChecker.hasCustomerPortalOnlyPermission(user, project);
    }

    @Override
    public boolean hasPermission(Issue issue, String parameter, ApplicationUser user, boolean issueCreation) {
        return customerPortalOnlyPermissionChecker.hasCustomerPortalOnlyPermission(user, issue);
    }

    @Override
    public Set<ApplicationUser> getUsers(PermissionContext permissionContext, String argument) {
        final Project project = permissionContext.getProjectObject();
        if (project == null) {
            log.warn("returning no users because project in the permission context was null");

            return emptySet();
        }

        return customerPortalOnlyPermissionChecker.getCustomerPortalOnlyPermissionUsers(project, EMPTY_QUERY, true);
    }

    @Nonnull
    @Override
    public Set<ApplicationUser> getUsers(@Nonnull final PermissionContext permissionContext,
                                         @Nullable final String argument,
                                         @Nonnull final String userSearchName,
                                         final int limit) {
        final Project project = permissionContext.getProjectObject();
        if (project == null) {
            log.warn("returning no users because project in the permission context was null");

            return emptySet();
        }

        return customerPortalOnlyPermissionChecker.getCustomerPortalOnlyPermissionUsers(project, userSearchName, false)
            .stream()
            .sorted(comparing(ApplicationUser::getDisplayName, comparing(IdentifierUtils::toLowerCase))
                .thenComparing(ApplicationUser::getUsername, comparing(IdentifierUtils::toLowerCase)))
            .filter(user -> userSearchMatchUser(user, userSearchName))
            .limit(limit)
            .collect(Collectors.toSet());
    }

    /**
     * This method is for validation when adding this type to their permission scheme. We have nothing to do here.
     */
    @Override
    public void doValidation(String key, Map<String, String> parameters, JiraServiceContext jiraServiceContext) {
    }

    /**
     * Validate if the anonymous user has permissions and CP is enabled for anon access
     */
    @Override
    public boolean hasPermission(Project project, String parameter) {
        return customerPortalOnlyPermissionChecker.hasCustomerPortalOnlyPermissionForAnonymous(project);
    }

    /**
     * Anonymous user will never be able to view issue
     */
    @Override
    public boolean hasPermission(Issue issue, String parameter) {
        return false;
    }
}
