package com.atlassian.servicedesk.internal.permission.security;

import com.atlassian.jira.bc.license.JiraLicenseService;
import com.atlassian.jira.config.FeatureManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.servicedesk.api.user.CheckedUser;
import com.atlassian.servicedesk.internal.api.feature.reqparticipants.group.CustomerGroupParticipantManager;
import com.atlassian.servicedesk.internal.api.user.UserFactoryOld;
import com.atlassian.servicedesk.internal.spi.permission.security.CustomerInvolvedType;
import com.atlassian.servicedesk.internal.spi.permission.security.RequestAccessUserStrategy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

import static com.atlassian.servicedesk.internal.api.featureflag.ServiceDeskFeatureFlags.SHARE_REQUEST_WITH_GROUP;
import static java.util.Collections.emptyList;

@Component
public class CustomerGroupParticipantRequestAccessUserStrategy implements RequestAccessUserStrategy {
    private final CustomerGroupParticipantManager customerGroupParticipantManager;
    private final UserFactoryOld userFactoryOld;
    private final JiraLicenseService jiraLicenseService;
    private final FeatureManager featureManager;

    public CustomerGroupParticipantRequestAccessUserStrategy(
        final CustomerGroupParticipantManager customerGroupParticipantManager,
        final UserFactoryOld userFactoryOld,
        final JiraLicenseService jiraLicenseService,
        final FeatureManager featureManager
    ) {
        this.customerGroupParticipantManager = customerGroupParticipantManager;
        this.userFactoryOld = userFactoryOld;
        this.jiraLicenseService = jiraLicenseService;
        this.featureManager = featureManager;
    }

    private boolean isEnabled() {
        return jiraLicenseService.isDataCenterLicensed()
            && featureManager.isEnabled(SHARE_REQUEST_WITH_GROUP);
    }

    @Override
    public List<CheckedUser> getUsers(@Nonnull final Issue issue) {
        if (isEnabled()) {
            final Collection<CheckedUser> users = customerGroupParticipantManager
                .getGroupMembersForIssue(issue)
                .getOrElse(emptyList());
            return new ArrayList<>(users);
        } else {
            return emptyList();
        }
    }

    @Override
    public CustomerInvolvedType getType() {
        return CustomerInvolvedType.CUSTOMER_GROUP;
    }

    @Override
    public boolean match(final ApplicationUser user, final Issue issue) {
        if (isEnabled()) {
            return userFactoryOld
                .wrap(user)
                .exists(checkedUser -> customerGroupParticipantManager.isMemberOfAnyGroupsInIssue(checkedUser, issue));
        } else {
            return false;
        }
    }
}
