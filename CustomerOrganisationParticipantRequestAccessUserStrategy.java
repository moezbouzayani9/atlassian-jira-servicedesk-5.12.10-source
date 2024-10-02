package com.atlassian.servicedesk.internal.permission.security;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.servicedesk.api.user.CheckedUser;
import com.atlassian.servicedesk.internal.api.user.UserFactoryOld;
import com.atlassian.servicedesk.internal.feature.reqparticipants.organization.CustomerOrganizationParticipantManager;
import com.atlassian.servicedesk.internal.spi.permission.security.CustomerInvolvedType;
import com.atlassian.servicedesk.internal.spi.permission.security.RequestAccessUserStrategy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static java.util.Collections.emptyList;

@Component
public class CustomerOrganisationParticipantRequestAccessUserStrategy implements RequestAccessUserStrategy {
    private final CustomerOrganizationParticipantManager customerOrganizationParticipantManager;
    private final UserFactoryOld userFactoryOld;

    @Autowired
    public CustomerOrganisationParticipantRequestAccessUserStrategy(CustomerOrganizationParticipantManager customerOrganizationParticipantManager,
                                                                    UserFactoryOld userFactoryOld) {
        this.customerOrganizationParticipantManager = customerOrganizationParticipantManager;
        this.userFactoryOld = userFactoryOld;
    }

    @Override
    public List<CheckedUser> getUsers(@Nonnull Issue issue) {
        final Collection<CheckedUser> users = customerOrganizationParticipantManager
            .getOrganizationMembersForIssue(issue)
            .getOrElse(emptyList());
        return new ArrayList<>(users);
    }

    @Override
    public CustomerInvolvedType getType() {
        return CustomerInvolvedType.CUSTOMER_ORGANISATION;
    }

    @Override
    public boolean match(ApplicationUser user, Issue issue) {
        return userFactoryOld.wrap(user)
            .exists(checkedUser -> customerOrganizationParticipantManager
                .isMemberOfAnyOrganizationsInIssue(checkedUser, issue));
    }
}
