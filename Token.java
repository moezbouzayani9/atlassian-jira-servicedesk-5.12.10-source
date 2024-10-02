package com.atlassian.servicedesk.internal.security;

import java.util.List;
import java.util.Objects;
import org.codehaus.jackson.annotate.JsonAutoDetect;

@JsonAutoDetect
public class Token {
    private String value;
    private long expiry;
    private List<Long> projectIds;

    public Token() {
    }

    public Token(final String value, final long expiry, final List<Long> projectIds) {
        this.value = value;
        this.expiry = expiry;
        this.projectIds = projectIds;
    }

    public String getValue() {
        return value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    public long getExpiry() {
        return expiry;
    }

    public void setExpiry(final long expiry) {
        this.expiry = expiry;
    }

    public List<Long> getProjectIds() {
        return projectIds;
    }

    public void setProjectIds(final List<Long> projectIds) {
        this.projectIds = projectIds;
    }

    public boolean containsProjectId(long projectId) {
        if (projectIds == null) {
            return false;
        }
        return projectIds.stream().anyMatch(id -> id == projectId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Token that = (Token) o;
        return expiry == that.expiry &&
            value.equals(that.value) &&
            Objects.equals(projectIds, that.projectIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, expiry, projectIds);
    }
}
