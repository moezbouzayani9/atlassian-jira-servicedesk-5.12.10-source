package com.atlassian.servicedesk.internal.permission.security;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.servicedesk.api.user.CheckedUser;
import com.atlassian.servicedesk.internal.api.user.UserFactoryOld;
import com.atlassian.servicedesk.internal.spi.permission.security.CustomerInvolvedType;
import com.atlassian.servicedesk.internal.spi.permission.security.RequestAccessUserStrategy;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static java.util.Collections.emptyList;

@Component
public class ReporterRequestAccessUserStrategy implements RequestAccessUserStrategy {
    private final UserFactoryOld userFactoryOld;

    @Autowired
    public ReporterRequestAccessUserStrategy(UserFactoryOld userFactoryOld) {
        this.userFactoryOld = userFactoryOld;
    }

    @Override
    public List<CheckedUser> getUsers(@Nonnull Issue issue) {
        if (issue.getReporterUser() == null) {
            return emptyList();
        }

        return userFactoryOld.wrap(issue.getReporterUser())
            .fold(
                error -> emptyList(),
                ImmutableList::of
            );
    }

    @Override
    public CustomerInvolvedType getType() {
        return CustomerInvolvedType.REPORTER;
    }

    @Override
    public boolean match(ApplicationUser user, Issue issue) {
        if (user == null || issue == null) {
            return false;
        } else {
            return user.getKey().equals(issue.getReporterId());
        }
    }
}
