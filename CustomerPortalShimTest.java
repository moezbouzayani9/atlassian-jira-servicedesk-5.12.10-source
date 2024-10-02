package com.atlassian.servicedesk.internal.web.shim;

import com.atlassian.jira.config.FeatureManager;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.AttachmentManager;
import com.atlassian.jira.issue.attachment.Attachment;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.servicedesk.api.user.SDUser;
import com.atlassian.servicedesk.internal.api.condition.SDOperationalConditionHelper;
import com.atlassian.servicedesk.internal.api.permission.anonymous.AnonymousPreconditionCheckingService;
import com.atlassian.servicedesk.internal.api.user.UserFactoryOld;
import com.atlassian.servicedesk.internal.featureflag.SDFeatureFlags;
import com.atlassian.servicedesk.internal.utils.CustomerUrlUtil;
import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static io.atlassian.fugue.Option.none;
import static io.atlassian.fugue.Option.some;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(org.mockito.junit.MockitoJUnitRunner.Silent.class)
public class CustomerPortalShimTest {

    private static final long ATTACHMENT_ID = 1L;
    private static final long ISSUE_ID = 2L;
    private static final String LOGIN_URL = "login-url";
    private static final String SERVICEDESK_CUSTOMERSHIM_SECURE_ATTACHMENT_URL = "/servicedesk/customershim/secure/attachment/url";

    @Mock
    private JiraAuthenticationContext jiraAuthenticationContext;
    @Mock
    private CustomerUrlUtil customerUrlUtil;
    @Mock
    private ApplicationProperties applicationProperties;
    @Mock
    private SDOperationalConditionHelper sdOperationalConditionHelper;
    @Mock
    private RequestAttachmentPermissionService requestAttachmentPermissionService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;
    @Mock
    private RequestDispatcher requestDispatcher;
    @Mock
    private AttachmentManager attachmentManager;
    @Mock
    private FeatureManager featureManager;
    @Mock
    private UserFactoryOld userFactory;
    @Mock
    private AnonymousPreconditionCheckingService anonymousPreconditionCheckingService;
    @Mock
    private Attachment attachment;
    @Mock
    private SDUser sdUser;

    private CustomerPortalShim customerPortalShim;

    @Before
    public void setup() {
        when(jiraAuthenticationContext.isLoggedInUser()).thenReturn(true);
        when(customerUrlUtil.getLoginUrl(anyMapOf(String.class, String.class), anyBoolean())).thenReturn(LOGIN_URL);
        when(applicationProperties.getEncoding()).thenReturn("UTF-8");
        when(sdOperationalConditionHelper.isConditionTrue()).thenReturn(true);

        customerPortalShim = spy(new CustomerPortalShim(
            jiraAuthenticationContext,
            customerUrlUtil,
            applicationProperties,
            sdOperationalConditionHelper,
            requestAttachmentPermissionService,
            attachmentManager,
            featureManager,
            userFactory,
            anonymousPreconditionCheckingService));
        when(userFactory.getSDUser()).thenReturn(sdUser);
        when(sdUser.isAnonymous()).thenReturn(true);
    }

    @Test
    public void doFilter__shim_not_applied_when_sd_not_licensed() throws Exception {
        when(sdOperationalConditionHelper.isConditionTrue()).thenReturn(false);

        customerPortalShim.doFilter(request, response, filterChain);
        verify(customerPortalShim, times(0)).doFilterWhenLicensed(request, response, filterChain);
    }

    @Test
    public void doFilter__request_redirected_when_user_not_logged_in() throws Exception {
        when(jiraAuthenticationContext.isLoggedInUser()).thenReturn(false);

        when(request.getRequestURI()).thenReturn("/servicedesk/customershim/rest/api/1.0/labels");
        customerPortalShim.doFilter(request, response, filterChain);

        verify(response).setHeader(eq("Cache-Control"), eq("no-cache"));
        verify(response).setStatus(eq(HttpServletResponse.SC_TEMPORARY_REDIRECT));
        verify(response).setHeader(eq("Location"), eq(LOGIN_URL));
    }

    @Test
    public void doFilter__request_forwarded_for_shimmed_urls() throws Exception {
        for (String url : CustomerUrlUtil.SHIM_WHITE_LIST) {
            String shimUrl = CustomerUrlUtil.SHIM_PREFIX + url;
            when(request.getRequestURI()).thenReturn(shimUrl);
            when(request.getRequestDispatcher(anyString())).thenReturn(requestDispatcher);

            customerPortalShim.doFilter(request, response, filterChain);

            ArgumentCaptor<String> urlCapture = ArgumentCaptor.forClass(String.class);
            verify(request).getRequestDispatcher(urlCapture.capture());
            verify(requestDispatcher).forward(request, response);
            assertThat(urlCapture.getValue(), equalTo(url));

            reset(request);
            reset(requestDispatcher);
        }
    }

    @Test
    public void doFilter__request_not_forwarded_for_urls_not_shimmed() throws Exception {
        for (String url : CustomerUrlUtil.SHIM_WHITE_LIST) {
            String shimUrl = CustomerUrlUtil.SHIM_PREFIX + "/not/in/shim" + url;
            when(request.getRequestURI()).thenReturn(shimUrl);

            customerPortalShim.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);

            reset(filterChain);
        }

    }

    @Test
    public void doFilter__request_not_forwarded_nonexistent_issue_attachment_shortcircuit_ff_on() throws Exception {
        when(request.getRequestURI()).thenReturn(SERVICEDESK_CUSTOMERSHIM_SECURE_ATTACHMENT_URL);
        when(jiraAuthenticationContext.isLoggedInUser()).thenReturn(true);
        when(requestAttachmentPermissionService.isAttachmentUrl(any())).thenReturn(true);
        when(featureManager.isEnabled(SDFeatureFlags.ATTACHMENT_IS_VIEWABLE_SHORTCIRCUIT)).thenReturn(true);
        when(requestAttachmentPermissionService.getAttachmentIdFromURI(any())).thenReturn(some(ATTACHMENT_ID));
        when(attachmentManager.getAttachment(any())).thenReturn(attachment);
        when(requestAttachmentPermissionService.getIssueIdFromRequest(any())).thenReturn(none());

        customerPortalShim.doFilter(request, response, filterChain);
        verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST));
    }

    @Test
    public void doFilter__request_not_forwarded_nonexistent_issue_attachment_shortcircuit_ff_off() throws Exception {
        when(request.getRequestURI()).thenReturn(SERVICEDESK_CUSTOMERSHIM_SECURE_ATTACHMENT_URL);
        when(jiraAuthenticationContext.isLoggedInUser()).thenReturn(true);
        when(requestAttachmentPermissionService.isAttachmentUrl(any())).thenReturn(true);
        when(featureManager.isEnabled(SDFeatureFlags.ATTACHMENT_IS_VIEWABLE_SHORTCIRCUIT)).thenReturn(false);
        when(requestAttachmentPermissionService.getAttachmentIdFromURI(any())).thenReturn(some(ATTACHMENT_ID));
        when(requestAttachmentPermissionService.getIssueIdFromRequest(any())).thenReturn(none());

        customerPortalShim.doFilter(request, response, filterChain);
        verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST));
    }

    @Test
    public void doFilter__request_not_forwarded_nonexistent_attachment_shortcircuit_ff_on() throws Exception {
        when(request.getRequestURI()).thenReturn(SERVICEDESK_CUSTOMERSHIM_SECURE_ATTACHMENT_URL);
        when(jiraAuthenticationContext.isLoggedInUser()).thenReturn(true);
        when(requestAttachmentPermissionService.isAttachmentUrl(any())).thenReturn(true);
        when(featureManager.isEnabled(SDFeatureFlags.ATTACHMENT_IS_VIEWABLE_SHORTCIRCUIT)).thenReturn(true);
        when(requestAttachmentPermissionService.getAttachmentIdFromURI(any())).thenReturn(none());
        when(requestAttachmentPermissionService.getIssueIdFromRequest(any())).thenReturn(some(ISSUE_ID));

        customerPortalShim.doFilter(request, response, filterChain);
        verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST));
    }

    @Test
    public void doFilter__request_not_forwarded_nonexistent_attachment_shortcircuit_ff_off() throws Exception {
        when(request.getRequestURI()).thenReturn(SERVICEDESK_CUSTOMERSHIM_SECURE_ATTACHMENT_URL);
        when(jiraAuthenticationContext.isLoggedInUser()).thenReturn(true);
        when(requestAttachmentPermissionService.isAttachmentUrl(any())).thenReturn(true);
        when(featureManager.isEnabled(SDFeatureFlags.ATTACHMENT_IS_VIEWABLE_SHORTCIRCUIT)).thenReturn(false);
        when(requestAttachmentPermissionService.getAttachmentIdFromURI(any())).thenReturn(none());
        when(requestAttachmentPermissionService.getIssueIdFromRequest(any())).thenReturn(some(ISSUE_ID));

        customerPortalShim.doFilter(request, response, filterChain);
        verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST));
    }

    @Test
    public void doFilter__request_not_forwarded_for_unviewable_attachment_shortcircuit_ff_on() throws Exception {
        when(request.getRequestURI()).thenReturn(SERVICEDESK_CUSTOMERSHIM_SECURE_ATTACHMENT_URL);
        when(jiraAuthenticationContext.isLoggedInUser()).thenReturn(true);
        when(requestAttachmentPermissionService.getAttachmentIdFromURI(any())).thenReturn(some(ATTACHMENT_ID));
        when(requestAttachmentPermissionService.isAttachmentUrl(any())).thenReturn(true);
        when(requestAttachmentPermissionService.getIssueIdFromRequest(any())).thenReturn(some(ISSUE_ID));
        when(attachmentManager.getAttachment(any())).thenReturn(attachment);
        when(featureManager.isEnabled(SDFeatureFlags.ATTACHMENT_IS_VIEWABLE_SHORTCIRCUIT)).thenReturn(true);
        when(requestAttachmentPermissionService.customerCanViewAttachment(anyLong(), any(Attachment.class))).thenReturn(false);

        customerPortalShim.doFilter(request, response, filterChain);
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN));
    }

    @Test
    public void doFilter__request_not_forwarded_for_unviewable_attachment_shortcircuit_ff_off() throws Exception {
        when(request.getRequestURI()).thenReturn(SERVICEDESK_CUSTOMERSHIM_SECURE_ATTACHMENT_URL);
        when(jiraAuthenticationContext.isLoggedInUser()).thenReturn(true);
        when(requestAttachmentPermissionService.getAttachmentIdFromURI(any())).thenReturn(some(ATTACHMENT_ID));
        when(requestAttachmentPermissionService.isAttachmentUrl(any())).thenReturn(true);
        when(requestAttachmentPermissionService.getIssueIdFromRequest(any())).thenReturn(some(ISSUE_ID));
        when(featureManager.isEnabled(SDFeatureFlags.ATTACHMENT_IS_VIEWABLE_SHORTCIRCUIT)).thenReturn(false);
        when(requestAttachmentPermissionService.customerCanViewAttachment(anyLong(), any(Long.class))).thenReturn(false);

        customerPortalShim.doFilter(request, response, filterChain);
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN));
    }

    @Test
    public void doFilter__request_forwarded_for_viewable_attachment_shortcircuit_ff_on() throws Exception {
        when(request.getRequestURI()).thenReturn(SERVICEDESK_CUSTOMERSHIM_SECURE_ATTACHMENT_URL);
        when(jiraAuthenticationContext.isLoggedInUser()).thenReturn(true);
        when(requestAttachmentPermissionService.getAttachmentIdFromURI(any())).thenReturn(some(ATTACHMENT_ID));
        when(requestAttachmentPermissionService.isAttachmentUrl(any())).thenReturn(true);
        when(requestAttachmentPermissionService.getIssueIdFromRequest(any())).thenReturn(some(ISSUE_ID));
        when(attachmentManager.getAttachment(any())).thenReturn(attachment);
        when(featureManager.isEnabled(SDFeatureFlags.ATTACHMENT_IS_VIEWABLE_SHORTCIRCUIT)).thenReturn(true);
        when(requestAttachmentPermissionService.customerCanViewAttachment(anyLong(), any(Attachment.class))).thenReturn(true);
        when(request.getRequestDispatcher(anyString())).thenReturn(requestDispatcher);

        customerPortalShim.doFilter(request, response, filterChain);
        ArgumentCaptor<String> urlCapture = ArgumentCaptor.forClass(String.class);
        verify(request).getRequestDispatcher(urlCapture.capture());
        verify(requestDispatcher).forward(request, response);

        reset(request);
        reset(requestDispatcher);
    }

    @Test
    public void doFilter__request_forwarded_for_viewable_attachment_shortcircuit_ff_off() throws Exception {
        when(request.getRequestURI()).thenReturn(SERVICEDESK_CUSTOMERSHIM_SECURE_ATTACHMENT_URL);
        when(jiraAuthenticationContext.isLoggedInUser()).thenReturn(true);
        when(requestAttachmentPermissionService.getAttachmentIdFromURI(any())).thenReturn(some(ATTACHMENT_ID));
        when(requestAttachmentPermissionService.isAttachmentUrl(any())).thenReturn(true);
        when(requestAttachmentPermissionService.getIssueIdFromRequest(any())).thenReturn(some(ISSUE_ID));
        when(featureManager.isEnabled(SDFeatureFlags.ATTACHMENT_IS_VIEWABLE_SHORTCIRCUIT)).thenReturn(false);
        when(requestAttachmentPermissionService.customerCanViewAttachment(anyLong(), any(Long.class))).thenReturn(true);
        when(request.getRequestDispatcher(anyString())).thenReturn(requestDispatcher);

        customerPortalShim.doFilter(request, response, filterChain);
        ArgumentCaptor<String> urlCapture = ArgumentCaptor.forClass(String.class);
        verify(request).getRequestDispatcher(urlCapture.capture());
        verify(requestDispatcher).forward(request, response);

        reset(request);
        reset(requestDispatcher);
    }

    @Test
    public void doFilter__view_avatar_request_forwarded_when_user_not_logged_in__and_anonymous_portal_is_enabled() throws Exception {
        when(jiraAuthenticationContext.isLoggedInUser()).thenReturn(false);
        when(anonymousPreconditionCheckingService.isAnonymousAccessAllowed()).thenReturn(true);
        when(request.getRequestURI()).thenReturn("/servicedesk/customershim/secure/viewavatar");
        when(request.getRequestDispatcher(anyString())).thenReturn(requestDispatcher);

        customerPortalShim.doFilter(request, response, filterChain);

        ArgumentCaptor<String> urlCapture = ArgumentCaptor.forClass(String.class);
        verify(request).getRequestDispatcher(urlCapture.capture());
        verify(requestDispatcher).forward(request, response);
        reset(request);
        reset(requestDispatcher);
    }
}
