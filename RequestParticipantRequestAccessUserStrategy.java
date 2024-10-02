package com.atlassian.servicedesk.internal.permission.security;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.servicedesk.api.user.CheckedUser;
import com.atlassian.servicedesk.internal.api.user.UserFactoryOld;
import com.atlassian.servicedesk.internal.customfields.participants.ParticipantsCustomFieldManager;
import com.atlassian.servicedesk.internal.spi.permission.security.CustomerInvolvedType;
import com.atlassian.servicedesk.internal.spi.permission.security.RequestAccessUserStrategy;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static java.util.Collections.emptySet;

@Component
public class RequestParticipantRequestAccessUserStrategy implements RequestAccessUserStrategy {

    private final UserFactoryOld userFactoryOld;
    private final ParticipantsCustomFieldManager participantsCustomFieldManager;

    @Autowired
    public RequestParticipantRequestAccessUserStrategy(final UserFactoryOld userFactoryOld,
                                                       final ParticipantsCustomFieldManager participantsCustomFieldManager) {
        this.userFactoryOld = userFactoryOld;
        this.participantsCustomFieldManager = participantsCustomFieldManager;
    }

    @Override
    public List<CheckedUser> getUsers(@Nonnull final Issue issue) {
        return ImmutableList.copyOf(getRequestParticipants(issue));
    }

    @Override
    public CustomerInvolvedType getType() {
        return CustomerInvolvedType.REQUEST_PARTICIPANT;
    }

    @Override
    public boolean match(final ApplicationUser user, final Issue issue) {
        return userFactoryOld.wrap(user)
            .exists(checkedUser -> getRequestParticipants(issue).contains(checkedUser));
    }

    private Set<CheckedUser> getRequestParticipants(final Issue issue) {
        return participantsCustomFieldManager.getUserParticipantsFromIssue(issue).getOrElse(emptySet());
    }
}
