package com.atlassian.servicedesk.plugins.rest.internal.dto.domain.comment;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.servicedesk.api.comment.ServiceDeskComment;
import com.atlassian.servicedesk.api.rest.dto.domain.comment.CommentDTO;
import com.atlassian.servicedesk.api.rest.dto.links.SelfLinkDTO;
import com.atlassian.servicedesk.internal.api.rest.DTOFactory;
import com.atlassian.servicedesk.plugins.rest.internal.dto.domain.date.DateDTOFactory;
import com.atlassian.servicedesk.plugins.rest.internal.dto.domain.user.UserDTOFactory;
import com.atlassian.servicedesk.plugins.rest.internal.dto.links.LinkFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CommentDTOFactory implements DTOFactory<ServiceDeskComment, CommentDTO> {
    private final UserDTOFactory userDTOFactory;
    private final DateDTOFactory dateDtoFactory;
    private final LinkFactory linkFactory;
    private final JiraAuthenticationContext jiraAuthenticationContext;

    @Autowired
    public CommentDTOFactory(UserDTOFactory userDTOFactory,
                             DateDTOFactory dateDtoFactory,
                             LinkFactory linkFactory,
                             JiraAuthenticationContext jiraAuthenticationContext) {
        this.userDTOFactory = userDTOFactory;
        this.dateDtoFactory = dateDtoFactory;
        this.linkFactory = linkFactory;
        this.jiraAuthenticationContext = jiraAuthenticationContext;
    }

    @Override
    public CommentDTO toDTO(ServiceDeskComment from) {
        final SelfLinkDTO selfLink = linkFactory
            .start()
            .path("request/{requestId}/comment/{commentId}")
            .build(from.getComment().getIssue().getId(), from.getComment().getId());

        return CommentDTO
            .builder()
            .setId(String.valueOf(from.getComment().getId()))
            .set_public(from.isPublic())
            .setBody(from.getComment().getBody())
            .setCreated(dateDtoFactory.toDTO(jiraAuthenticationContext.getLoggedInUser(), from.getComment().getCreated()))
            .setAuthor(userDTOFactory.toDTO(from.getComment().getAuthorApplicationUser()))
            .set_links(selfLink)
            .build();
    }
}
