package com.atlassian.servicedesk.internal.rest.temporary;

import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.pocketknife.api.commons.error.AnError;
import com.atlassian.pocketknife.api.commons.jira.ErrorResultHelper;
import com.atlassian.pocketknife.api.commons.result.Unit;
import io.atlassian.fugue.Either;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static io.atlassian.fugue.Either.left;
import static io.atlassian.fugue.Either.right;

/**
 * Problem statement: Currently we require CREATE + BROWSE permission for a customer in order for them to see the
 * customer portal. We would like to move back to just using BROWSE for this purpose, i.e. every user with BROWSE
 * permission on a project will have access to the portal.
 * <p/>
 * This however can get us into a permission elevation case for users that currently have BROWSE but don't have CREATE
 * <p/>
 * This helper will find those affected customers. Having this information will allow us to make an informed choice
 * whether we should go ahead with removing the CREATE restriction.
 */
@Component
class CustomerPortalPermissionsUtil {

    private final GlobalPermissionManager globalPermissionManager;
    private final ErrorResultHelper errorResultHelper;

    @Autowired
    public CustomerPortalPermissionsUtil(GlobalPermissionManager globalPermissionManager,
                                         ErrorResultHelper errorResultHelper) {
        this.globalPermissionManager = globalPermissionManager;
        this.errorResultHelper = errorResultHelper;
    }

    Either<AnError, Unit> canUseResource(ApplicationUser user) {
        if (!hasPermission(user)) {
            final AnError anError = errorResultHelper.anError(403, "Only sysadmin and site admins can execute this resource");
            return left(anError);
        } else {
            return right(Unit.UNIT);
        }
    }

    private boolean hasPermission(ApplicationUser user) {
        return isSysAdmin(user) || isSiteAdmin(user);
    }

    private boolean isSysAdmin(ApplicationUser user) {
        return globalPermissionManager.hasPermission(GlobalPermissionKey.SYSTEM_ADMIN, user);
    }

    private boolean isSiteAdmin(ApplicationUser user) {
        return isJIRAAdmin(user);
    }

    private boolean isJIRAAdmin(ApplicationUser user) {
        return globalPermissionManager.hasPermission(GlobalPermissionKey.ADMINISTER, user);
    }
}