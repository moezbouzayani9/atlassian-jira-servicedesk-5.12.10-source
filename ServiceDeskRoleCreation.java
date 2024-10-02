package com.atlassian.servicedesk.internal.permission.security.role;

import com.atlassian.servicedesk.internal.user.permission.roles.ServiceDeskJiraRoleManager;
import com.atlassian.servicedesk.internal.user.permission.roles.ServiceDeskProjectRole;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ServiceDeskRoleCreation {

    private final ServiceDeskJiraRoleManager serviceDeskJIRARoleManager;

    private static final List<ServiceDeskProjectRole> ROLES = ImmutableList.of(
        ServiceDeskProjectRole.CUSTOMER,
        ServiceDeskProjectRole.TEAM
    );

    @Autowired
    public ServiceDeskRoleCreation(ServiceDeskJiraRoleManager serviceDeskJIRARoleManager) {
        this.serviceDeskJIRARoleManager = serviceDeskJIRARoleManager;
    }

    public void createServiceDeskRoles() {
        ROLES.forEach(
            serviceDeskJIRARoleManager::getOrCreateRole
        );
    }

}
