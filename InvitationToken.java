package com.atlassian.servicedesk.internal.security;

import java.util.List;
import java.util.Objects;
import org.codehaus.jackson.annotate.JsonAutoDetect;

@JsonAutoDetect
public class InvitationToken extends Token {
    private boolean isHelpCenter;

    public InvitationToken() {
        super();
    }

    public InvitationToken(String value, long expiry, List<Long> projectIds) {
        super(value, expiry, projectIds);
        this.isHelpCenter = false;
    }

    public InvitationToken(String value, long expiry, List<Long> projectIds, boolean isHelpCenter) {
        super(value, expiry, projectIds);
        setHelpCenter(isHelpCenter);
    }

    @Override
    public void setProjectIds(List<Long> projectIds) {
        if (isHelpCenter && !projectIds.isEmpty()) {
            throw new IllegalArgumentException("InvitationToken can not have projects set if isHelpCenter is true");
        }
        super.setProjectIds(projectIds);
    }

    public boolean isHelpCenter() {
        return isHelpCenter;
    }

    /**
     * Can only be true if {@link #getProjectIds()} is empty
     * @param helpCenter whether this invite is to the help center
     */
    public void setHelpCenter(boolean helpCenter) {
        if (isHelpCenter && getProjectIds() != null && !getProjectIds().isEmpty()) {
            throw new IllegalArgumentException("InvitationToken can only be for help center if no projects are specified");
        }
        isHelpCenter = helpCenter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InvitationToken that = (InvitationToken) o;
        return super.equals(o) && isHelpCenter == that.isHelpCenter;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getValue(), getExpiry(), getProjectIds(), isHelpCenter);
    }
}
