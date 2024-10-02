package it.com.atlassian.servicedesk.rest;

import com.atlassian.jira.testkit.client.rules.FeatureFlagRule;
import com.atlassian.servicedesk.test.category.DCEnvironmentOnly;
import com.atlassian.servicedesk.test.data.model.UserRole;
import com.atlassian.servicedesk.test.runner.ServiceDeskStatelessTestRunner;
import com.atlassian.servicedesk.test.runner.fixtures.Fixture;
import com.atlassian.servicedesk.test.runner.fixtures.SDProjectFixture;
import com.atlassian.servicedesk.test.runner.fixtures.UserFixture;
import com.atlassian.servicedesk.webtest.client.RequestSecuritySettingsClient;
import com.atlassian.servicedesk.webtest.json.models.requestsecurity.RequestSecuritySettingsRequest;
import com.atlassian.servicedesk.webtest.json.models.requestsecurity.RequestSecuritySettingsResponse;
import com.sun.jersey.api.client.ClientResponse;
import io.atlassian.fugue.Either;
import it.com.atlassian.servicedesk.backdoor.SignupBackdoorClient;
import it.com.atlassian.servicedesk.util.BaseServiceDeskIntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static com.google.common.collect.ImmutableList.of;
import static it.com.atlassian.servicedesk.data.constants.SDIntegrationTestFeatureFlags.SHARING_WITH_GROUPS;
import static it.com.atlassian.servicedesk.util.DefaultTestData.JIRA_ADMINISTRATORS_GROUP_NAME;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_PRECONDITION_FAILED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@Category(DCEnvironmentOnly.class)
@RunWith(ServiceDeskStatelessTestRunner.class)
public class RequestSecuritySettingsResourceTest extends BaseServiceDeskIntegrationTest {

    @Rule
    public final FeatureFlagRule FEATURE_FLAG_RULE = new FeatureFlagRule(backdoor.getTestkit(), of(SHARING_WITH_GROUPS()));

    @Fixture
    private final UserFixture admin = UserFixture.createUser().build();

    @Fixture
    private final UserFixture agent = UserFixture.createUser().build();
    private final RequestSecuritySettingsClient requestSecuritySettingsClient = new RequestSecuritySettingsClient(backdoor.env());
    private final SignupBackdoorClient signupBackdoorClient = backdoor.signupBackdoorClient();
    private final boolean isGlobalSignupEnabled = signupBackdoorClient.isGlobalSignupEnabled();
    @Fixture
    private final UserFixture sysAdmin = UserFixture.createUser().groups(JIRA_ADMINISTRATORS_GROUP_NAME).build();
    @Fixture
    private final SDProjectFixture project = SDProjectFixture.createProject(admin)
        .withUser(admin, UserRole.ADMIN, UserRole.AGENT)
        .withUser(sysAdmin, UserRole.ADMIN, UserRole.AGENT)
        .withUser(agent, UserRole.AGENT)
        .build();

    @Before
    public void setup() {
        requestSecuritySettingsClient.as(admin);
    }

    @After
    public void cleanup() {
        if (isGlobalSignupEnabled) {
            signupBackdoorClient.enableGlobalSignup();
        } else {
            signupBackdoorClient.disableGlobalSignup();
        }
    }

    /* Scala converted tests cases, will have funky method names. */
    /* Names are not changed because the testing logic is not changed. */
    @Test
    public void default_request_security_settings() {
        final Either<ClientResponse, RequestSecuritySettingsResponse> requestSecuritySettings =
            requestSecuritySettingsClient.getRequestSecuritySettings(project.get().getProjectKey());

        assertThat(requestSecuritySettings.isRight(), is(true));

        assertThat(requestSecuritySettings.right().get().isManageEnabled(), is(false));
        assertThat(requestSecuritySettings.right().get().isAutocompleteEnabled(), is(false));
        assertThat(requestSecuritySettings.right().get().isServiceDeskOpenAccess(), is(true));
        assertThat(requestSecuritySettings.right().get().isServiceDeskPublicSignup(), is(false));
        assertThat(requestSecuritySettings.right().get().isPortalVoteEnabled(), is(false));
        assertThat(requestSecuritySettings.right().get().isCustomerGroupShareEnabled(), is(false));
    }

    @Test
    public void project_admin_wants_to_update_participants_settings() {
        testApplyValidSettings(true, true, false, false, false, false);
        testApplyValidSettings(true, false, false, false, false, false);
        testApplyValidSettings(false, false, false, false, false, false);
        //with new setting the setting is independent
        testApplyValidSettings(false, true, false, false, false, false);
        testApplyValidSettings(false, true, false, false, false, true);
    }


    @Test
    public void project_admin_wants_to_allow_public_signup_on_her_portal_when_ashton_has_enabled_it() {
        backdoor.signupBackdoorClient().enableGlobalSignup();

        testApplyValidSettings(false, false, true, true, false, false);
    }

    @Test
    public void project_admin_wants_to_allow_public_signup_on_her_portal_when_ashton_has_disabled_it() {
        backdoor.signupBackdoorClient().disableGlobalSignup();
        final RequestSecuritySettingsRequest request = new RequestSecuritySettingsRequest(
            false,
            false,
            true,
            true,
            false,
            false);

        final int error = requestSecuritySettingsClient.updateRequestSecuritySettingsWithError(project.get().getProjectKey(), request);

        assertThat(error, is(SC_FORBIDDEN));
    }

    @Test
    public void project_admin_wants_to_disallow_public_signup_on_her_portal_even_though_ashton_has_made_it_possible() {
        backdoor.signupBackdoorClient().enableGlobalSignup();

        testApplyValidSettings(false, false, true, false, false, false);
    }

    @Test
    public void project_admin_wants_tomake_on_her_portal_closed_access_but_public_signup_is_incorrectly_enabled() {
        backdoor.signupBackdoorClient().enableGlobalSignup();

        final RequestSecuritySettingsRequest request = new RequestSecuritySettingsRequest(
            false,
            false,
            false,
            true,
            false,
            false);

        final int error = requestSecuritySettingsClient.updateRequestSecuritySettingsWithError(project.get().getProjectKey(), request);

        assertThat(error, is(SC_PRECONDITION_FAILED));
    }

    @Test
    public void project_admin_wants_to_make_her_portal_closed_access() {
        backdoor.signupBackdoorClient().enableGlobalSignup();

        testApplyValidSettings(false, false, false, false, false, false);
    }

    @Test
    public void sys_admin_wants_to_allow_public_signup_on_a_project_when_it_is_currently_disabled_globally() {
        backdoor.signupBackdoorClient().enableGlobalSignup();
        requestSecuritySettingsClient.as(sysAdmin);

        testApplyValidSettings(false, false, true, true, false, false);
    }

    @Test
    public void sys_admin_wants_to_enable_customer_portal_voting() {
        backdoor.signupBackdoorClient().enableGlobalSignup();
        requestSecuritySettingsClient.as(sysAdmin);

        testApplyValidSettings(false, false, true, false, true, false);
    }

    @Test
    public void project_agent_wants_to_update_request_security_settings() {
        requestSecuritySettingsClient.as(agent);
        final RequestSecuritySettingsRequest request = new RequestSecuritySettingsRequest(
            false,
            false,
            true,
            true,
            false,
            false);

        final int error = requestSecuritySettingsClient.updateRequestSecuritySettingsWithError(project.get().getProjectKey(), request);

        assertThat(error, is(SC_FORBIDDEN));
    }

    @Test
    public void admin_wants_to_enable_customer_group_sharing() {
        backdoor.signupBackdoorClient().enableGlobalSignup();

        testApplyValidSettings(false, false, true, false, false, true);
    }

    private void testApplyValidSettings(final boolean manageEnabled,
                                        final boolean autocompleteEnabled,
                                        final boolean openAccess,
                                        final boolean publicSignup,
                                        final boolean portalVoteEnabled,
                                        final boolean customerGroupShareEnabled) {
        final RequestSecuritySettingsRequest request = new RequestSecuritySettingsRequest(manageEnabled, autocompleteEnabled, openAccess, publicSignup, portalVoteEnabled, customerGroupShareEnabled);

        final Either<ClientResponse, RequestSecuritySettingsResponse> response = requestSecuritySettingsClient.updateRequestSecuritySettings(project.get().getProjectKey(), request);
        assertThat(response.isRight(), is(true));

        final RequestSecuritySettingsResponse requestSecuritySettingsResponse = response.right().get();
        assertThat(requestSecuritySettingsResponse.isManageEnabled(), is(manageEnabled));
        assertThat(requestSecuritySettingsResponse.isAutocompleteEnabled(), is(autocompleteEnabled));
        assertThat(requestSecuritySettingsResponse.isServiceDeskOpenAccess(), is(openAccess));
        assertThat(requestSecuritySettingsResponse.isServiceDeskPublicSignup(), is(publicSignup));
        assertThat(requestSecuritySettingsResponse.isPortalVoteEnabled(), is(portalVoteEnabled));
        assertThat(requestSecuritySettingsResponse.isCustomerGroupShareEnabled(), is(customerGroupShareEnabled));
    }
}
