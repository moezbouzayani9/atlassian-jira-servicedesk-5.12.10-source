package com.atlassian.servicedesk.internal.permission.misconfiguration;

import com.atlassian.jira.permission.MockProjectPermission;
import com.atlassian.jira.permission.ProjectPermission;
import com.atlassian.jira.permission.ProjectPermissionCategory;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.servicedesk.internal.permission.misconfiguration.error.MisconfigurationInformation;
import com.atlassian.servicedesk.internal.user.permission.roles.ServiceDeskJiraRoleManager;
import com.atlassian.servicedesk.internal.user.permission.roles.ServiceDeskProjectRole;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static io.atlassian.fugue.Option.some;
import static java.util.Collections.emptyList;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class CustomerRolePermissionConfigurationCheckerTest {
    @Mock
    private PermissionSchemeUtil permissionSchemeUtil;

    @Mock
    private ServiceDeskJiraRoleManager serviceDeskJIRARoleManager;

    @Mock
    private ProjectRole projectRole;

    @Mock
    private Project project;

    @Mock
    private PermissionManager permissionManager;

    @Mock
    private I18nHelper i18nHelper;

    private CustomerRolePermissionConfigurationChecker permissionConfigurationChecker;

    @Before
    public void setUp() {
        permissionConfigurationChecker = new CustomerRolePermissionConfigurationChecker(
            permissionSchemeUtil,
            serviceDeskJIRARoleManager,
            permissionManager,
            i18nHelper
        );

        when(i18nHelper.getText(anyString())).thenAnswer(returnsFirstArg());
        when(serviceDeskJIRARoleManager.getRole(ServiceDeskProjectRole.CUSTOMER)).thenReturn(some(projectRole));
    }

    @Test
    public void permission_configured_is_invalid_if_customer_role_is_assigned_to_any_project_permission() {
        ProjectPermission browsePermission = new MockProjectPermission("BROWSE_PROJECTS", "admin.permissions.BROWSE", "", ProjectPermissionCategory.PROJECTS);
        ProjectPermission assignIssuePermission = new MockProjectPermission("ASSIGN_ISSUES", "admin.permissions.ASSIGN_ISSUE", "", ProjectPermissionCategory.ISSUES);

        doReturn(some(browsePermission)).when(permissionManager).getProjectPermission(ProjectPermissions.BROWSE_PROJECTS);
        doReturn(some(assignIssuePermission)).when(permissionManager).getProjectPermission(ProjectPermissions.ASSIGN_ISSUES);

        when(permissionSchemeUtil.getPermissionsForRoleAndProject(eq(project), any())).thenReturn(ImmutableList.of(ProjectPermissions.BROWSE_PROJECTS, ProjectPermissions.ASSIGN_ISSUES));

        List<MisconfigurationInformation> result = permissionConfigurationChecker.apply(project);

        assertThat("Critical error is generated", result, contains(
            new MisconfigurationInformationMatcher(
                Severity.Critical,
                ImmutableList.of("admin.permissions.BROWSE", "admin.permissions.ASSIGN_ISSUE"),
                ImmutableList.of(ProjectPermissions.BROWSE_PROJECTS, ProjectPermissions.ASSIGN_ISSUES)
            )
        ));
    }

    @Test
    public void customer_role_is_not_assigned_to_any_permissions() {
        when(permissionSchemeUtil.getPermissionsForRoleAndProject(eq(project), any())).thenReturn(emptyList());

        List<MisconfigurationInformation> result = permissionConfigurationChecker.apply(project);

        assertThat("No misconfiguration information", result, is(empty()));
    }
}
