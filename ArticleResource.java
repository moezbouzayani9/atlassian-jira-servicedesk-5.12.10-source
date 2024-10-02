package com.atlassian.servicedesk.plugins.kb.internal.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.pocketknife.api.commons.error.AnError;
import com.atlassian.pocketknife.step.Steps;
import com.atlassian.servicedesk.api.user.SDUser;
import com.atlassian.servicedesk.internal.api.permission.anonymous.AnonymousPreconditionCheckingService;
import com.atlassian.servicedesk.internal.api.rest.RestResponseHelper;
import com.atlassian.servicedesk.internal.api.user.UserFactoryOld;
import com.atlassian.servicedesk.plugins.kb.internal.article.ArticleService;
import com.atlassian.servicedesk.plugins.kb.internal.errors.CommonKBErrors;
import com.atlassian.servicedesk.plugins.kb.internal.model.IsSearchQueryOverLimitResponse;
import com.atlassian.servicedesk.plugins.kb.internal.search.ArticlesRequestQuery;
import io.atlassian.fugue.Either;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.atlassian.servicedesk.internal.api.knowledgebase.cloud.ConfluenceCloudConstants.CONFLUENCE_CLOUD_LINK_ID;
import static io.atlassian.fugue.Either.right;
import static io.atlassian.fugue.Option.option;
import static java.util.Collections.emptySet;
import static java.util.Optional.empty;
import static java.util.Optional.of;

@Path("/articles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ArticleResource {

    private static final String DEFAULT_PAGE_NUMBER = "1";
    private static final String DEFAULT_RESULTS_PER_PAGE_COUNT = "3";
    private static final String DEFAULT_HIGHLIGHT_VALUE = "true";
    public static final String APP_LINK_NOT_SUPPORTED = "sd.knowledge.base.applink.not.supported";

    private final JiraAuthenticationContext jiraAuthenticationContext;
    private final ArticleService articleService;
    private final CommonKBErrors commonKBErrors;
    private final RestResponseHelper restResponseHelper;
    private final AnonymousPreconditionCheckingService anonymousPreconditionCheckingService;
    private final UserFactoryOld userFactoryOld;

    public ArticleResource(
        JiraAuthenticationContext jiraAuthenticationContext,
        ArticleService articleService,
        CommonKBErrors commonKBErrors,
        RestResponseHelper restResponseHelper,
        AnonymousPreconditionCheckingService anonymousPreconditionCheckingService,
        UserFactoryOld userFactoryOld) {
        this.jiraAuthenticationContext = jiraAuthenticationContext;
        this.articleService = articleService;
        this.commonKBErrors = commonKBErrors;
        this.restResponseHelper = restResponseHelper;
        this.anonymousPreconditionCheckingService = anonymousPreconditionCheckingService;
        this.userFactoryOld = userFactoryOld;
    }

    @Path("/search-limit")
    @GET
    public Response checkSpaceKeyLimit(@QueryParam("projectKey") final String projectKey){
        return articleService.isOverConfluenceQueryLimit(userFactoryOld.getSDUser(), projectKey)
            .fold(
                restResponseHelper::anErrorToResponse,
                result -> Response.ok(new IsSearchQueryOverLimitResponse(result)).build()
            );
    }

    @Path("/shared/search")
    @AnonymousAllowed
    @GET
    public Response searchArticlesInSharedPortal(
        @QueryParam("query") String query,
        @QueryParam("highlight") @DefaultValue(DEFAULT_HIGHLIGHT_VALUE) Boolean highlight,
        @QueryParam("pageNumber") @DefaultValue(DEFAULT_PAGE_NUMBER) int pageNumber,
        @QueryParam("resultsPerPage") @DefaultValue(DEFAULT_RESULTS_PER_PAGE_COUNT) int resultsPerPage
    ) {
        SDUser user = userFactoryOld.getSDUser();

        return Steps.begin(anonymousPreconditionCheckingService.canAccessAnonymousResource(user))
            .then(unit -> getArticleRequestQuery(user.forJIRA(), query, emptySet(), highlight, pageNumber, resultsPerPage, empty(), empty()))
            .then((unit, articlesRequestQuery) -> articleService.searchArticlesInSharedPortal(articlesRequestQuery))
            .yield((unit, articlesRequestQuery, articleSearchResults) -> articleSearchResults)
            .fold(
                restResponseHelper::anErrorToResponse,
                articleSearchResults -> Response.ok(articleSearchResults).build()
            );
    }

    @Path("/search")
    @AnonymousAllowed
    @GET
    public Response searchArticlesInProjectContext(
        @QueryParam("query") String query,
        @QueryParam("project") String projectKey,
        @QueryParam("label") Set<String> labels,
        @QueryParam("highlight") @DefaultValue(DEFAULT_HIGHLIGHT_VALUE) Boolean highlight,
        @QueryParam("pageNumber") @DefaultValue(DEFAULT_PAGE_NUMBER) int pageNumber,
        @QueryParam("resultsPerPage") @DefaultValue(DEFAULT_RESULTS_PER_PAGE_COUNT) int resultsPerPage
    ) {
        SDUser user = userFactoryOld.getSDUser();

        return Steps.begin(anonymousPreconditionCheckingService.canAccessAnonymousResource(user))
            .then(unit -> getArticleRequestQuery(user.forJIRA(), query, labels, highlight, pageNumber, resultsPerPage, of(projectKey), empty()))
            .then((unit, articlesRequestQuery) -> articleService.searchArticlesInProjectContext(articlesRequestQuery))
            .yield((unit, articlesRequestQuery, articleSearchResults) -> articleSearchResults)
            .fold(
                restResponseHelper::anErrorToResponse,
                articleSearchResults -> Response.ok(articleSearchResults).build()
            );
    }

    @Path("/{issueKey}")
    @GET
    public Response getRelatedArticlesFromIssue(
        @PathParam("issueKey") String issueKey,
        @QueryParam("query") String query,
        @QueryParam("highlight") @DefaultValue("false") Boolean highlight,
        @QueryParam("pageNumber") @DefaultValue(DEFAULT_PAGE_NUMBER) int pageNumber,
        @QueryParam("resultsPerPage") @DefaultValue(DEFAULT_RESULTS_PER_PAGE_COUNT) int resultsPerPage
    ) {
        return Steps.begin(getLoggedinUser())
            .then(user -> getArticleRequestQuery(user, query, emptySet(), highlight, pageNumber, resultsPerPage, empty(), of(issueKey)))
            .then((user, articlesRequestQuery) -> articleService.searchArticlesInIssueContext(articlesRequestQuery))
            .yield((user, articlesRequestQuery, articleSearchResults) -> articleSearchResults)
            .fold(
                restResponseHelper::anErrorToResponse,
                articleSearchResults -> Response.ok(articleSearchResults).build()
            );
    }

    @Path("/remotepageview/{applicationLinkId}/{pageId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response getConfluenceToken(@PathParam("applicationLinkId") String applicationLinkId,
                                       @PathParam("pageId") long pageId) {

        if (applicationLinkId.equals(CONFLUENCE_CLOUD_LINK_ID)) {
            return restResponseHelper.badRequest(APP_LINK_NOT_SUPPORTED);
        }
        return Steps.begin(getLoggedinUser())
            .then(user -> articleService.getConfluenceRedirectUrlWithToken(user, applicationLinkId, pageId))
            .yield((user, confluenceTokenResult) -> confluenceTokenResult)
            .fold(
                restResponseHelper::anErrorToResponse,
                confluenceTokenResult -> Response.ok(confluenceTokenResult).build()
            );
    }

    private Either<AnError, ApplicationUser> getLoggedinUser() {
        return option(jiraAuthenticationContext.getLoggedInUser()).toRight(commonKBErrors::NOT_LOGGED_IN);
    }

    private Either<AnError, ArticlesRequestQuery> getArticleRequestQuery(
        ApplicationUser user,
        String query,
        Set<String> labels,
        Boolean highlight,
        int pageNumber,
        int resultsPerPage,
        Optional<String> projectKeyOpt,
        Optional<String> issueKeyOpt
    ) {
        ArticlesRequestQuery articlesRequestQuery = articleService.newArticleRequestQueryBuilder(user)
            .query(query)
            .highlight(highlight)
            .pageNumber(pageNumber)
            .resultsPerPage(resultsPerPage)
            .projectKeyOpt(projectKeyOpt)
            .issueKeyOpt(issueKeyOpt)
            .labels(labels)
            .build();

        return right(articlesRequestQuery);
    }

}
