package com.atlassian.servicedesk.internal.permission.security.type;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.permission.PermissionTypeManager;
import com.atlassian.jira.security.SecurityTypeManager;
import com.atlassian.jira.security.type.SecurityType;
import com.atlassian.jira.util.I18nHelper;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This contains methods that install and uninstall service project specific security types. Jira normally loads security types from
 * permission-types.xml. We are installing ourselves at runtime until this is a proper plugin point. We have to insert into the PermissionTypeManager
 * and the SecurityTypeManager because both are used and both are instanciated separately.
 */
@Component
public class ServiceDeskSecurityTypeInstaller {

    private final I18nHelper i18nHelper;
    private final CustomerPortalOnlyPermissionChecker customerPortalOnlyPermissionChecker;

    @Autowired
    public ServiceDeskSecurityTypeInstaller(I18nHelper i18nHelper,
                                            CustomerPortalOnlyPermissionChecker customerPortalOnlyPermissionChecker) {
        this.i18nHelper = i18nHelper;
        this.customerPortalOnlyPermissionChecker = customerPortalOnlyPermissionChecker;
    }

    public void install() {
        // Load the permission type manager
        PermissionTypeManager permissionTypeManager = getPermissionTypeManager();
        // Load the security type manager
        SecurityTypeManager securityTypeManager = getSecurityTypeManager();

        // Create the security types we want to install
        CustomerPortalOnlySecurityType securityType = new CustomerPortalOnlySecurityTypeImpl(i18nHelper, customerPortalOnlyPermissionChecker);

        // We need to inject our security type into these both of these services. They are identical and Jira uses both in different places. They both
        // load from the same .xml file and are lazy loaded, which means if we insert into one and not the other, we won't get consistent behaviour
        addPermissionType(securityTypeManager, securityType);
        addPermissionType(permissionTypeManager, securityType);
    }

    public void uninstall() {
        // Load the permission type manager
        PermissionTypeManager permissionTypeManager = getPermissionTypeManager();
        // Load the security type manager
        SecurityTypeManager securityTypeManager = getSecurityTypeManager();

        // We need to remove our security type from these two services for completeness
        removePermissionType(permissionTypeManager);
        removePermissionType(securityTypeManager);
    }

    private void addPermissionType(SecurityTypeManager securityTypeManager,
                                   CustomerPortalOnlySecurityType customerPortalOnlySecurityType) {
        // Add our type into the map and set it on the manager. Call getTypes here instead of getSecurityTypes because it will load them from the .xml
        // if they
        // aren't loaded yet.
        Map<String, SecurityType> securityTypes = new HashMap<String, SecurityType>(securityTypeManager.getTypes());
        securityTypes.put(CustomerPortalOnlySecurityType.TYPE, customerPortalOnlySecurityType);
        securityTypeManager.setSecurityTypes(securityTypes);
    }

    private void removePermissionType(SecurityTypeManager securityTypeManager) {
        // remove our types from the map and save it
        Map<String, SecurityType> securityTypes = new HashMap<String, SecurityType>(securityTypeManager.getTypes());
        securityTypes.remove(CustomerPortalOnlySecurityType.TYPE);
        securityTypeManager.setSecurityTypes(securityTypes);
    }

    private PermissionTypeManager getPermissionTypeManager() {
        return ComponentAccessor.getComponent(PermissionTypeManager.class);
    }

    private SecurityTypeManager getSecurityTypeManager() {
        return ComponentAccessor.getComponent(SecurityTypeManager.class);
    }
}
