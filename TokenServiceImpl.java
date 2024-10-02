package com.atlassian.servicedesk.internal.security;

import com.atlassian.jira.project.Project;
import com.atlassian.pocketknife.api.commons.error.AnError;
import com.atlassian.pocketknife.api.commons.jira.ErrorResultHelper;
import com.atlassian.security.utils.ConstantTimeComparison;
import com.atlassian.servicedesk.JSDSuccess;
import com.atlassian.servicedesk.api.user.CheckedUser;
import com.atlassian.servicedesk.internal.feature.customer.user.ServiceDeskUserManager;
import com.atlassian.servicedesk.internal.feature.customer.user.signup.GlobalPublicSignupService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Either;
import io.atlassian.fugue.Option;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static io.atlassian.fugue.Either.left;
import static io.atlassian.fugue.Either.right;
import static io.atlassian.fugue.Option.some;
import static java.util.stream.Collectors.toSet;

@Component
public class TokenServiceImpl implements TokenService {

    static final String INVITATION_TOKEN_KEY = "local.servicedesk.outstanding.agent.or.customer.invite";
    static final String EMAIL_CHANNEL_TOKEN_KEY = "local.servicedesk.email.channel.token";
    private static final String INVALID_TOKEN = "sd.email.agent.invitation.invalid.token";

    private final ServiceDeskUserManager sdUserManager;
    private final GlobalPublicSignupService globalPublicSignupService;
    private final TokenUtils tokenUtils;
    private final ErrorResultHelper errorResultHelper;

    @Autowired
    public TokenServiceImpl(final ServiceDeskUserManager sdUserManager,
                            final GlobalPublicSignupService globalPublicSignupService,
                            final TokenUtils tokenUtils,
                            final ErrorResultHelper errorResultHelper) {
        this.sdUserManager = sdUserManager;
        this.globalPublicSignupService = globalPublicSignupService;
        this.tokenUtils = tokenUtils;
        this.errorResultHelper = errorResultHelper;
    }
    @Override
    public InvitationToken createInviteToken(final CheckedUser user,
                                             final List<Project> projects,
                                             final boolean isHelpCenter,
                                             final boolean isAnonymous) {
        final InvitationToken token = tokenUtils.generateInviteToken(user, projects, isHelpCenter);
        addInviteTokenToUser(user, token, isAnonymous);
        return token;
    }

    @Override
    public Token createEmailChannelToken(final CheckedUser user, final Project project) {
        final Token token = tokenUtils.generateToken(user, Collections.singletonList(project));
        addEmailChannelTokenToUser(user, token);
        return token;
    }

    @Override
    public Either<AnError, JSDSuccess> validateInviteToken(final CheckedUser user,
                                                           final Option<Project> project,
                                                           final String targetTokenKey) {
        final Either<AnError, Boolean> result = getInviteTokens(user).map(tokens ->
            tokens.stream().anyMatch(token -> validateToken(token, project, targetTokenKey))
        );

        return result.fold(
            e -> left(errorResultHelper.badRequest400(INVALID_TOKEN).build()),
            validToken -> {
                if (validToken) {
                    return right(JSDSuccess.success());
                } else {
                    return left(errorResultHelper.badRequest400(INVALID_TOKEN).build());
                }
            });
    }

    @Override
    public Either<AnError, JSDSuccess> clearInviteTokens(final CheckedUser user) {
        return sdUserManager.removeUserAttributes(user, ImmutableList.of(INVITATION_TOKEN_KEY));
    }

    @Override
    public Either<AnError, JSDSuccess> clearEmailChannelTokens(final CheckedUser user) {
        return sdUserManager.removeUserAttributes(user, ImmutableList.of(EMAIL_CHANNEL_TOKEN_KEY));
    }

    @Override
    public boolean isEmailChannelTokenValid(final CheckedUser user,
                                            final Project project,
                                            final String targetTokenKey) {
        final Either<AnError, Boolean> result = getEmailChannelTokens(user).map(tokens ->
            tokens.stream().anyMatch(token -> validateToken(token, some(project), targetTokenKey)));

        return result.fold(
            e -> false,
            validationResult -> validationResult
        );
    }

    /**
     * Package private for testing
     * Make a constant time comparison to prevent a timing attack (<a href="http://codahale.com/a-lesson-in-timing-attacks/">...</a>)
     *
     * @param tokenKey
     * @param targetTokenKey
     * @return result
     */
    boolean constantTimeTokenComparison(final String tokenKey, final String targetTokenKey) {
        return ConstantTimeComparison.isEqual(tokenKey, targetTokenKey);
    }

    /**
     * Add new token to user attribute per invitation
     *
     * @param user        The user to add the token to
     * @param token       The token to add
     * @param isAnonymous Flag if the user is anonymous. In this case we always overwrite existing tokens with the same
     *                    permissions
     */
    private void addInviteTokenToUser(final CheckedUser user,
                                      final InvitationToken token,
                                      final boolean isAnonymous) {
        final String tokenValue = tokenUtils.toJson(token);
        if (isAnonymous || globalPublicSignupService.isEmailVerificationEnabled()) {
            // Remove existing token with the same permission
            getInviteTokenStringSet(user)
                .map(currentTokens -> getDifferentInviteToken(currentTokens, token))
                .flatMap(differentTokens -> setInviteTokenStringSet(user, ImmutableSet.<String>builder().addAll(differentTokens).add(tokenValue).build()));
            return;
        }

        getInviteTokenStringSet(user)
            .flatMap(currentTokens -> setInviteTokenStringSet(user, ImmutableSet.<String>builder().addAll(currentTokens).add(tokenValue).build()));
    }

    private void addEmailChannelTokenToUser(final CheckedUser user, final Token token) {
        final String tokenValue = tokenUtils.toJson(token);
        getEmailChannelTokenStringSet(user)
            .flatMap(currentTokens -> setEmailChannelTokenStringSet(user, ImmutableSet.<String>builder().addAll(currentTokens).add(tokenValue).build()));
    }

    /**
     * Find tokens that have different {@link InvitationToken#getProjectIds()} and {@link InvitationToken#isHelpCenter()}
     * @param tokens The set of existing tokens
     * @param token The token to look for
     * @return A set of tokens such that the project ids and help center status are not equal to <code>token</code>
     */
    private Set<String> getDifferentInviteToken(final Set<String> tokens, final InvitationToken token) {
        return tokens.stream()
            .map(tokenUtils::inviteTokenFromJson)
            .filter(existingToken -> !(existingToken.getProjectIds().equals(token.getProjectIds()) && existingToken.isHelpCenter() == token.isHelpCenter()))
            .map(tokenUtils::toJson)
            .collect(toSet());
    }

    /**
     * Get all stored invite tokens for a user
     */
    private Either<AnError, Set<InvitationToken>> getInviteTokens(final CheckedUser user) {
        return getInviteTokenStringSet(user).map(tokenSet ->
            tokenSet.stream().map(tokenUtils::inviteTokenFromJson).collect(toSet())
        );
    }

    private Either<AnError, Set<Token>> getEmailChannelTokens(final CheckedUser user) {
        return getEmailChannelTokenStringSet(user).map(tokenSet ->
            tokenSet.stream().map(tokenUtils::emailChannelTokenFromJson).collect(toSet())
        );
    }

    private Either<AnError, Set<String>> getTokenStringSet(final CheckedUser user, final String tokenKey) {
        return sdUserManager.getUserAttributeSetCaseInsensitive(user, tokenKey);
    }

    private Either<AnError, Set<String>> getInviteTokenStringSet(final CheckedUser user) {
        return getTokenStringSet(user, INVITATION_TOKEN_KEY);
    }

    private Either<AnError, Set<String>> getEmailChannelTokenStringSet(final CheckedUser user) {
        return getTokenStringSet(user, EMAIL_CHANNEL_TOKEN_KEY);
    }

    private Either<AnError, JSDSuccess> setTokenStringSet(final CheckedUser user,
                                                          final Set<String> tokens,
                                                          final String tokenKey) {
        return sdUserManager.updateUserAttributeSet(user, ImmutableMap.of(tokenKey, tokens));
    }

    private Either<AnError, JSDSuccess> setInviteTokenStringSet(final CheckedUser user, final Set<String> tokens) {
        return setTokenStringSet(user, tokens, INVITATION_TOKEN_KEY);
    }

    private Either<AnError, JSDSuccess> setEmailChannelTokenStringSet(final CheckedUser user, final Set<String> tokens) {
        return setTokenStringSet(user, tokens, EMAIL_CHANNEL_TOKEN_KEY);
    }

    /**
     * Validate token values
     */
    private boolean validateToken(final Token token, final Option<Project> projectOpt, final String targetTokenKey) {
        final boolean isTokenMatched = constantTimeTokenComparison(token.getValue(), targetTokenKey);
        final boolean notExpired = tokenUtils.notExpired(token.getExpiry());
        final boolean validProjectId = (token instanceof InvitationToken && ((InvitationToken) token).isHelpCenter()) ||
            projectOpt.map(project -> token.containsProjectId(project.getId())).getOrElse(false);

        return isTokenMatched && notExpired && validProjectId;
    }
}
