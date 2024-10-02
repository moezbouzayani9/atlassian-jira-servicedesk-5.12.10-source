package com.atlassian.servicedesk.internal.permission.security;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.servicedesk.api.user.CheckedUser;
import com.atlassian.servicedesk.internal.spi.permission.security.CustomerInvolvedType;
import java.util.List;

public interface RequestAccessUserStrategyManager {

    /**
     * Get a list of users who involve to a request as a customer in the portal, i.e.
     * reporter, request participant, approver or organization member.
     *
     * WARNING: This method applies permission checks to all potential participants be it org or group members before
     * returning the list. It is proven to be extremely slow on large instances.
     */
    List<CheckedUser> getMembers(Issue issue);

    /**
     * Get a list of users of the given types who involve to a request as a customer in the portal
     */
    List<CheckedUser> getMembersForTypes(Issue issue, CustomerInvolvedType... types);

    /**
     * Check whether a user's type matches the strategy type in context of given issue, i.e.
     * reporter, request participant or approver.
     * <p>
     * This does not check access to the project the issue belongs to.
     */
    boolean match(ApplicationUser user, Issue issue);

    /**
     * Check whether a user's type matches any of the strategy types given in context of given issue, i.e.
     * <p>
     * This does not check access to the project the issue belongs to.
     */
    boolean match(ApplicationUser user, Issue issue, CustomerInvolvedType... types);
}
