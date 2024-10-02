package com.atlassian.servicedesk.internal.security;

import com.atlassian.jira.project.Project;
import com.atlassian.pocketknife.api.commons.error.AnError;
import com.atlassian.servicedesk.JSDSuccess;
import com.atlassian.servicedesk.api.user.CheckedUser;
import io.atlassian.fugue.Either;
import io.atlassian.fugue.Option;
import java.util.List;

public interface TokenService {
    /**
     * Create and store a new invite token for a list of projects
     *
     * @param user The user to create the token for
     * @param projects The list of projects to associate with the token
     * @param isHelpCenter Flags if the token is help center
     * @param isAnonymous Flags if the token is for an anonymous account
     */
    InvitationToken createInviteToken(CheckedUser user, List<Project> projects, boolean isHelpCenter, boolean isAnonymous);

    /**
     * Create and store a new emial channel token for a project
     *
     * @param user The user to create the token for
     * @param project The project to associate with the token
     */
    Token createEmailChannelToken(CheckedUser user, Project project);
    Either<AnError, JSDSuccess> validateInviteToken(CheckedUser user, Option<Project> project, String targetTokenKey);

    Either<AnError, JSDSuccess> clearInviteTokens(CheckedUser user);
    Either<AnError, JSDSuccess> clearEmailChannelTokens(CheckedUser user);

    boolean isEmailChannelTokenValid(CheckedUser checkedUser, Project project, String targetTokenKey);
}
