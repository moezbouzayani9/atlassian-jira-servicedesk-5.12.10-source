package com.atlassian.servicedesk.plugins.rest.internal.resource;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.plugins.rest.common.security.CorsAllowed;
import com.atlassian.pocketknife.api.commons.error.AnError;
import com.atlassian.rest.annotation.ResponseType;
import com.atlassian.servicedesk.api.ServiceDesk;
import com.atlassian.servicedesk.api.rest.dto.domain.servicedesk.ServiceDeskDTO;
import com.atlassian.servicedesk.api.rest.dto.paging.PagedDTO;
import com.atlassian.servicedesk.api.rest.dto.paging.RestPagedRequest;
import com.atlassian.servicedesk.api.util.paging.PagedResponse;
import com.atlassian.servicedesk.internal.api.ServiceDeskServiceOld;
import com.atlassian.servicedesk.plugins.rest.internal.annotations.PublicRestApi;
import com.atlassian.servicedesk.plugins.rest.internal.dto.domain.servicedesk.ServiceDeskDTOFactory;
import com.atlassian.servicedesk.plugins.rest.internal.examples.ServiceDeskExamples;
import com.atlassian.servicedesk.plugins.rest.internal.util.ResponseFactory;
import io.atlassian.fugue.Either;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

/**
 * This resource represents a service project. A service project is based on a project in the Jira platform, and is used to manage customer requests.
 */
@Path("servicedesk")
@CorsAllowed
public class ServiceDeskResource extends CommonResource {
    private final ServiceDeskServiceOld serviceDeskService;
    private final ServiceDeskDTOFactory serviceDeskDTOFactory;

    public ServiceDeskResource(final JiraAuthenticationContext jiraAuthenticationContext,
                               final ServiceDeskServiceOld serviceDeskService,
                               final ServiceDeskDTOFactory serviceDeskDTOFactory,
                               final ResponseFactory responseFactory) {
        super(jiraAuthenticationContext, responseFactory);
        this.serviceDeskService = serviceDeskService;
        this.serviceDeskDTOFactory = serviceDeskDTOFactory;
    }

    /**
     * Returns all service projects in the Jira Service Management application with the option to include archived service projects.
     *
     * @param includeArchived The option to include archived service project. False by default.
     * @param start           The starting index of the returned objects. Base index: 0.
     *                        See the <a href="#pagination">Pagination</a> section for more details.
     * @param limit           The maximum number of items to return per page. Default: 50
     *                        See the <a href="#pagination">Pagination</a> section for more details.
     * @response.representation.200.mediaType application/json
     * @response.representation.200.doc Returns the service projects, at the specified page of the results.
     * @response.representation.200.example {@link ServiceDeskExamples#EXAMPLE_PAGE}
     * @response.representation.401.doc Returned if the user is not logged in.
     */
    @GET
    @ResponseType(value = PagedDTO.class, genericTypes = ServiceDeskDTO.class, status = 200)
    @PublicRestApi
    public Response getServiceDesks(@QueryParam("includeArchived") @DefaultValue("false") final boolean includeArchived,
                                    @QueryParam(RestPagedRequest.START_QUERY_PARAM_NAME) final Integer start,
                                    @QueryParam(RestPagedRequest.LIMIT_QUERY_PARAM_NAME) final Integer limit) {

        Either<AnError, PagedResponse<ServiceDesk>> result = serviceDeskService.getServiceDesks(
            user(),
            includeArchived,
            RestPagedRequest.fromQueryParams(start, limit)
        );
        return responseFactory.pagedReponse(result, serviceDeskDTOFactory);
    }

    /**
     * Returns the service project for a given service project Id.
     *
     * @response.representation.200.mediaType application/json
     * @response.representation.200.doc Returns the requested service project.
     * @response.representation.200.example {@link ServiceDeskExamples#EXAMPLE}
     * @response.representation.401.doc Returned if the user is not logged in.
     * @response.representation.403.doc Returned if the user does not have permission to access the service project.
     * @response.representation.404.doc Returned if service project does not exist.
     */
    @GET
    @Path("{serviceDeskId}")
    @ResponseType(ServiceDeskDTO.class)
    @PublicRestApi
    public Response getServiceDeskById(@PathParam("serviceDeskId") int serviceDeskId) {
        Either<AnError, ServiceDesk> result = serviceDeskService.getServiceDeskById(user(), serviceDeskId);
        return responseFactory.entityResponse(result, serviceDeskDTOFactory);
    }
}
