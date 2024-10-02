package com.atlassian.servicedesk.internal.permission.security;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.servicedesk.api.user.CheckedUser;
import com.atlassian.servicedesk.internal.api.user.UserFactoryOld;
import com.atlassian.servicedesk.internal.spi.permission.security.CustomerInvolvedType;
import com.atlassian.servicedesk.internal.spi.permission.security.RequestAccessUserStrategy;
import com.atlassian.servicedesk.internal.utils.context.ServiceDeskOutsideCustomerPermissionContext;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static io.atlassian.fugue.Suppliers.alwaysFalse;
import static java.util.stream.Collectors.toList;

@Component
public class CustomerOutsiderRequestAccessUserStrategy implements RequestAccessUserStrategy {

    private final ServiceDeskOutsideCustomerPermissionContext serviceDeskOutsideCustomerPermissionContext;
    private final UserFactoryOld userFactoryOld;

    @Autowired
    public CustomerOutsiderRequestAccessUserStrategy(
        ServiceDeskOutsideCustomerPermissionContext serviceDeskOutsideCustomerPermissionContext,
        UserFactoryOld userFactoryOld) {
        this.serviceDeskOutsideCustomerPermissionContext = serviceDeskOutsideCustomerPermissionContext;
        this.userFactoryOld = userFactoryOld;
    }

    @Override
    public List<CheckedUser> getUsers(@Nonnull Issue issue) {
        return serviceDeskOutsideCustomerPermissionContext.getOutsideCustomerContext()
            .stream()
            .filter(context -> Objects.equals(context.getIssue(), issue))
            .map(ServiceDeskOutsideCustomerPermissionContext.OutsideCustomerIssuePermissionContext::getCheckedUser)
            .collect(toList());
    }

    @Override
    public CustomerInvolvedType getType() {
        return CustomerInvolvedType.CUSTOMER_OUTSIDER;
    }

    @Override
    public boolean match(ApplicationUser user, Issue issue) {
        return userFactoryOld.wrap(user)
            .map(checkedUser -> serviceDeskOutsideCustomerPermissionContext.isInIssueOutsideCustomerContext(checkedUser, issue))
            .getOr(alwaysFalse());
    }

}
