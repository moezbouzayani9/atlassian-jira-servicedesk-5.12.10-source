package com.atlassian.servicedesk.backdoor.sla.data;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.servicedesk.internal.api.visiblefortesting.LastProcessedTimestampBackdoor;
import io.atlassian.fugue.Option;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

@AnonymousAllowed
@Path("/sla/data/lastprocessedtimestamp")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LastProcessedTimestampBackdoorResource {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd");

    private final LastProcessedTimestampBackdoor lastProcessedTimestampBackdoor;
    private final JiraAuthenticationContext jiraAuthenticationContext;

    public LastProcessedTimestampBackdoorResource(final LastProcessedTimestampBackdoor lastProcessedTimestampBackdoor,
                                                  final JiraAuthenticationContext jiraAuthenticationContext) {
        this.lastProcessedTimestampBackdoor = lastProcessedTimestampBackdoor;
        this.jiraAuthenticationContext = jiraAuthenticationContext;
    }

    @POST
    @Path("/setLastProcessedTimestamp")
    public Response setLastProcessedTimestamp(@QueryParam("issueKey") String issueKey, @QueryParam("timestamp") String timestamp) {
        final ApplicationUser user = jiraAuthenticationContext.getLoggedInUser();
        final DateTime lastProcessedTimestamp = DATE_TIME_FORMATTER.parseDateTime(timestamp);

        lastProcessedTimestampBackdoor.setLastProcessedTimestamp(user, issueKey, lastProcessedTimestamp);

        return Response.ok().build();
    }

    @DELETE
    @Path("/clearLastProcessedTimestamp")
    public Response clearLastProcessedTimestamp(final @QueryParam("issueKey") String issueKey) {
        final ApplicationUser user = jiraAuthenticationContext.getLoggedInUser();

        lastProcessedTimestampBackdoor.clearLastProcessedTimestamp(user, issueKey);

        return Response.ok().build();
    }

    @GET
    @Path("/getLastProcessedTimestamp")
    public Response getLastProcessedTimestamp(@QueryParam("issueKey") String issueKey) {
        final ApplicationUser user = jiraAuthenticationContext.getLoggedInUser();
        final Option<DateTime> dateTimeOption = lastProcessedTimestampBackdoor.getLastProcessedTimestamp(user, issueKey);

        return dateTimeOption.fold(
            () -> Response.status(Response.Status.NOT_FOUND).build(),
            dateTime -> Response.ok(dateTime).build()
        );
    }
}
