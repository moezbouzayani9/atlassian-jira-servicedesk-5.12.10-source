package com.atlassian.servicedesk.internal.security;

import com.atlassian.crowd.util.SecureRandomStringUtils;
import com.atlassian.jira.project.Project;
import com.atlassian.security.random.DefaultSecureTokenGenerator;
import com.atlassian.servicedesk.api.user.CheckedUser;
import java.io.IOException;
import java.util.List;
import org.codehaus.jackson.map.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static java.util.stream.Collectors.toList;

@Component
public class TokenUtils {
    private final Logger logger = LoggerFactory.getLogger(TokenUtils.class);

    private final ObjectMapper mapper = new ObjectMapper();

    public String toJson(Token invitationToken) {
        try {
            return mapper.writeValueAsString(invitationToken);
        } catch (IOException e) {
            logger.error("Unable to convert invitation token to json {}", invitationToken);
        }
        return null;
    }

    public InvitationToken inviteTokenFromJson(String tokenAsJson) {
        try {
            return mapper.readValue(tokenAsJson, InvitationToken.class);
        } catch (IOException e) {
            logger.error("Unable to parse invitation token from json {}", tokenAsJson);
        }
        return null;
    }

    public Token emailChannelTokenFromJson(final String tokenAsJson) {
        try {
            return mapper.readValue(tokenAsJson, Token.class);
        } catch (IOException e) {
            logger.error("Unable to parse invitation token from json {}", tokenAsJson);
        }
        return null;
    }

    public String generatePassword() {
        return SecureRandomStringUtils.getInstance().randomAlphanumericString(26) + "ABab23$!@";
    }

    public Token generateToken(final CheckedUser user, final List<Project> projects) {
        final String tokenKey = genSecureToken(user);
        final long expiryTime = genInviteExpiryTime();
        final List<Long> projectIds = projects.stream().map(Project::getId).collect(toList());

        return new Token(tokenKey, expiryTime, projectIds);
    }

    public InvitationToken generateInviteToken(CheckedUser user, List<Project> projects, boolean isHelpCenter) {
        String tokenKey = genSecureToken(user);
        Long expiryTime = genInviteExpiryTime();
        List<Long> projectIds = projects.stream().map(Project::getId).collect(toList());

        return new InvitationToken(tokenKey, expiryTime, projectIds, isHelpCenter);
    }

    public boolean notExpired(Long milis) {
        return milis > now().getMillis();
    }

    private String genSecureToken(CheckedUser user) {
        return DefaultSecureTokenGenerator.getInstance().generateToken();
    }

    private Long genInviteExpiryTime() {
        Integer TOKEN_EXPIRY_DAYS = 28;

        return now().plusDays(TOKEN_EXPIRY_DAYS).getMillis();
    }

    private DateTime now() {
        return new DateTime(DateTimeZone.UTC);
    }
}
