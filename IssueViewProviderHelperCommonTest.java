package com.atlassian.servicedesk.internal.feature.customer.portal.providers.issue;

import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.license.JiraLicenseService;
import com.atlassian.jira.config.FeatureManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutManager;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.issue.status.category.StatusCategory;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.MockGroup;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.pocketknife.api.commons.jira.ErrorResultHelper;
import com.atlassian.pocketknife.api.commons.jira.TestableErrorResultHelper;
import com.atlassian.pocketknife.api.customfields.service.GlobalCustomFieldService;
import com.atlassian.servicedesk.api.organization.CustomerOrganization;
import com.atlassian.servicedesk.api.portal.Portal;
import com.atlassian.servicedesk.api.user.CheckedUser;
import com.atlassian.servicedesk.bootstrap.upgrade.helper.AddCustomFieldToScreensHelper;
import com.atlassian.servicedesk.internal.api.date.DateFormatter;
import com.atlassian.servicedesk.internal.api.date.ServiceDeskDateFormatterFactory;
import com.atlassian.servicedesk.internal.api.feature.customer.portal.PortalInternal;
import com.atlassian.servicedesk.internal.api.feature.reqparticipants.organization.CustomerOrganizationParticipantService;
import com.atlassian.servicedesk.internal.api.portal.InternalPortalService;
import com.atlassian.servicedesk.internal.api.request.requesttype.status.RequestStatusMapper;
import com.atlassian.servicedesk.internal.api.sla.SLAIssueService;
import com.atlassian.servicedesk.internal.api.user.permission.ServiceDeskLicenseAndPermissionService;
import com.atlassian.servicedesk.internal.customfields.groups.GroupsCFManagerImpl;
import com.atlassian.servicedesk.internal.customfields.groups.GroupsCFType;
import com.atlassian.servicedesk.internal.feature.customer.request.activitystream.RequestActivityManager;
import com.atlassian.servicedesk.internal.feature.customer.request.activitystream.providers.StatusAndResolutionActivityProvider;
import com.atlassian.servicedesk.internal.feature.customer.request.activitystream.responses.ActivityResponseManager;
import com.atlassian.servicedesk.internal.feature.customer.request.avatar.SDAgentAvatarManager;
import com.atlassian.servicedesk.internal.feature.group.error.CustomerGroupError;
import com.atlassian.servicedesk.internal.feature.jira.issue.IssueHelper;
import com.atlassian.servicedesk.internal.feature.reqparticipants.field.RequestParticipantsInternalService;
import com.atlassian.servicedesk.internal.rest.responses.CustomerRequestView;
import com.atlassian.servicedesk.internal.rest.responses.SimpleUserResponse;
import com.google.common.collect.ImmutableList;
import io.atlassian.fugue.Either;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static com.atlassian.servicedesk.internal.api.featureflag.ServiceDeskFeatureFlags.SHARE_REQUEST_WITH_GROUP;
import static io.atlassian.fugue.Option.option;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class IssueViewProviderHelperCommonTest {
    @Mock
    private Issue issue;
    private IssueViewProviderHelperCommon issueViewHelper;
    @Mock
    private Portal portal;
    @Mock
    private ServiceDeskDateFormatterFactory serviceDeskDateFormatterFactory;
    @Mock
    private RequestActivityManager requestActivityManager;
    @Mock
    private ActivityResponseManager activityResponseManager;
    @Mock
    private StatusAndResolutionActivityProvider statusAndResolutionActivityProvider;
    @Mock
    private ServiceDeskLicenseAndPermissionService serviceDeskLicenseAndPermissionService;
    @Mock
    private RequestParticipantsInternalService requestParticipantInternalService;
    @Mock
    private SDAgentAvatarManager agentAvatarManager;
    @Mock
    private CustomerOrganizationParticipantService organisationParticipantService;
    @Mock
    private IssueHelper issueHelper;
    @Mock
    private InternalPortalService internalPortalService;
    @Mock
    private SLAIssueService slaIssueService;
    @Mock
    private RequestStatusMapper statusMapper;
    @Mock
    private CheckedUser loggedInUser;
    @Mock
    private CheckedUser participant;
    @Mock
    private Collection<CustomerOrganization> mockedCustomerOrganization;
    @Mock
    private PortalInternal portalInternal;
    @Mock
    private DateFormatter dateFormatter;
    @Mock
    private Status status;
    @Mock
    private StatusCategory statusCategory;
    @Mock
    private CheckedUser reporterUser;
    @Mock
    private CheckedUser assigneeUser;
    @Mock
    private GlobalCustomFieldService globalCustomFieldService;
    @Mock
    private FeatureManager featureManager;
    @Mock
    private JiraLicenseService jiraLicenseService;
    @Mock
    private AddCustomFieldToScreensHelper addCustomFieldToScreenHelper;
    @Mock
    private CustomField customField;
    @Mock
    private GroupsCFType groupsCFType;
    @Mock
    private FieldLayoutManager fieldLayoutManager;
    @Mock
    private IssueService issueService;
    @Mock
    private JiraAuthenticationContext jiraAuthenticationContext;
    @Mock
    private I18nHelper.BeanFactory beanFactory;

    @Before
    public void setUp() throws Exception {

        final ErrorResultHelper errorResultHelper = new TestableErrorResultHelper(jiraAuthenticationContext, beanFactory);
        final CustomerGroupError customerGroupError = new CustomerGroupError(errorResultHelper);

        final GroupsCFManagerImpl groupsCFManager = new GroupsCFManagerImpl(globalCustomFieldService, featureManager, jiraLicenseService, addCustomFieldToScreenHelper,
            fieldLayoutManager, customerGroupError, issueService);

        issueViewHelper = new IssueViewProviderHelperCommon(serviceDeskDateFormatterFactory,
            requestActivityManager,
            activityResponseManager,
            statusAndResolutionActivityProvider,
            serviceDeskLicenseAndPermissionService,
            requestParticipantInternalService,
            agentAvatarManager,
            organisationParticipantService,
            groupsCFManager,
            issueHelper,
            internalPortalService,
            slaIssueService);

        when(requestParticipantInternalService.getValidParticipants(loggedInUser, issue)).thenReturn(Either.right(ImmutableList.of(participant)));
        when(organisationParticipantService.getOrganizationsForIssue(loggedInUser, issue)).thenReturn(Either.right(mockedCustomerOrganization));
        when(jiraLicenseService.isDataCenterLicensed()).thenReturn(true);
        when(issueHelper.getReporterOpt(issue)).thenReturn(option(reporterUser));
        when(issueHelper.getAssigneeOpt(issue)).thenReturn(option(assigneeUser));
        when(featureManager.isEnabled(SHARE_REQUEST_WITH_GROUP)).thenReturn(true);
        when(internalPortalService.toPortalInternal(portal)).thenReturn(portalInternal);
        when(issue.getKey()).thenReturn("key-123");
        when(issue.getCreated()).thenReturn(new Timestamp(System.currentTimeMillis()));
        when(serviceDeskDateFormatterFactory.forUser(loggedInUser)).thenReturn(dateFormatter);
        when(issue.getStatus()).thenReturn(status);
        when(issue.getStatus().getStatusCategory()).thenReturn(statusCategory);
        when(participant.getEmailAddress()).thenReturn("vlunic@atlassian.com");
    }

    @Test
    public void show_groups_in_result_when_ff_and_isDC_checks_are_on() {

        when(globalCustomFieldService.getGlobalCustomField(any())).thenReturn(customField);
        when(groupsCFType.getValueFromIssue(customField, issue)).thenReturn(getGroups());
        when(customField.getCustomFieldType()).thenReturn(groupsCFType);

        final CustomerRequestView view = issueViewHelper.getIssueViewCommon(loggedInUser, issue, portal, 1, "Key", "Name", statusMapper, null, 1);
        assertThat("three results expected", view.getGroups(), hasSize(3));
    }

    @Test
    public void show_empty_groups_when_ff_is_on_and_isDC_check_is_off() {
        when(jiraLicenseService.isDataCenterLicensed()).thenReturn(false);

        final CustomerRequestView view = issueViewHelper.getIssueViewCommon(loggedInUser, issue, portal, 1, "Key", "Name", statusMapper, null, 1);
        assertThat("zero results expected", view.getGroups(), hasSize(0));
    }

    @Test
    public void show_empty_groups_when_ff_is_off_and_isDC_check_is_on() {
        when(featureManager.isEnabled(SHARE_REQUEST_WITH_GROUP)).thenReturn(false);

        final CustomerRequestView view = issueViewHelper.getIssueViewCommon(loggedInUser, issue, portal, 1, "Key", "Name", statusMapper, null, 1);
        assertThat("zero results expected", view.getGroups(), hasSize(0));
    }

    @Test
    public void show_participants_email_addresses_when_user_is_agent() {

        when(serviceDeskLicenseAndPermissionService.canViewAgentView(loggedInUser, issue)).thenReturn(true);

        final CustomerRequestView view = issueViewHelper.getIssueViewCommon(loggedInUser, issue, portal, 1, "Key", "Name", statusMapper, null, 1);

        final String email = view.getParticipants().stream()
            .map(SimpleUserResponse::getEmail).findAny().orElse(null);

        assertNotNull(email);
    }

    @Test
    public void show_participants_email_addresses_when_user_is_admin() {

        when(serviceDeskLicenseAndPermissionService.canAdministerServiceDesk(loggedInUser, issue.getProjectObject())).thenReturn(true);

        final CustomerRequestView view = issueViewHelper.getIssueViewCommon(loggedInUser, issue, portal, 1, "Key", "Name", statusMapper, null, 1);

        final String email = view.getParticipants().stream()
            .map(SimpleUserResponse::getEmail).findAny().orElse(null);

        assertNotNull(email);
    }

    @Test
    public void dont_show_participants_email_addresses_when_user_is_not_admin_or_agent() {
        doReturn(false).when(serviceDeskLicenseAndPermissionService).canViewAgentView(loggedInUser, issue);

        final CustomerRequestView view = issueViewHelper.getIssueViewCommon(loggedInUser, issue, portal, 1, "Key", "Name", statusMapper, null, 1);

        final String email = view.getParticipants().stream()
            .filter(p -> p.getEmail() != null)
            .map(m -> m.getEmail())
            .findAny().orElse(null);

        assertNull(email);
    }

    private Collection<Group> getGroups() {
        final Group g1 = new MockGroup("g1");
        final Group g2 = new MockGroup("g2");
        final Group g3 = new MockGroup("g3");

        return new HashSet<>(Arrays.asList(g1, g2, g3));
    }

}
