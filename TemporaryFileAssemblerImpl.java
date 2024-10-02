package com.atlassian.servicedesk.plugins.rest.internal.resource.assembler;

import com.atlassian.jira.issue.AttachmentError;
import com.atlassian.jira.issue.AttachmentValidator;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.attachment.TemporaryWebAttachmentManager;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.xsrf.XsrfTokenGenerator;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.SimpleErrorCollection;
import com.atlassian.plugins.rest.common.multipart.FilePart;
import com.atlassian.plugins.rest.common.multipart.fileupload.CommonsFileUploadFilePart;
import com.atlassian.plugins.rest.common.security.jersey.XsrfResourceFilter;
import com.atlassian.pocketknife.api.commons.error.AnError;
import com.atlassian.pocketknife.api.commons.jira.ErrorResultHelper;
import com.atlassian.pocketknife.step.Steps;
import com.atlassian.servicedesk.api.rest.dto.domain.attachment.CreateTemporaryWebAttachmentResultDTO;
import com.atlassian.servicedesk.api.rest.dto.domain.attachment.TemporaryWebAttachmentDTO;
import com.atlassian.servicedesk.internal.api.ServiceDeskServiceOld;
import com.atlassian.servicedesk.plugins.rest.internal.dto.domain.attachment.TemporaryWebAttachmentDTOFactory;
import com.google.common.collect.Lists;
import io.atlassian.fugue.Either;
import io.atlassian.fugue.Eithers;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TemporaryFileAssemblerImpl implements TemporaryFileAssembler {
    private final ServiceDeskServiceOld serviceDeskService;
    private final TemporaryWebAttachmentManager temporaryWebAttachmentManager;
    private final AttachmentValidator attachmentValidator;
    private final XsrfTokenGenerator xsrfGenerator;
    private final ProjectManager projectManager;
    private final ErrorResultHelper errorResultHelper;
    private final TemporaryWebAttachmentDTOFactory temporaryWebAttachmentDTOFactory;

    @Autowired
    public TemporaryFileAssemblerImpl(final ServiceDeskServiceOld serviceDeskService,
                                      final TemporaryWebAttachmentManager temporaryWebAttachmentManager,
                                      final AttachmentValidator attachmentValidator,
                                      final XsrfTokenGenerator xsrfGenerator,
                                      final ProjectManager projectManager,
                                      final ErrorResultHelper errorResultHelper,
                                      final TemporaryWebAttachmentDTOFactory temporaryWebAttachmentDTOFactory) {
        this.serviceDeskService = serviceDeskService;
        this.temporaryWebAttachmentManager = temporaryWebAttachmentManager;
        this.attachmentValidator = attachmentValidator;
        this.xsrfGenerator = xsrfGenerator;
        this.projectManager = projectManager;
        this.errorResultHelper = errorResultHelper;
        this.temporaryWebAttachmentDTOFactory = temporaryWebAttachmentDTOFactory;
    }

    @Override
    public Either<AnError, CreateTemporaryWebAttachmentResultDTO> attachTemporaryFiles(ApplicationUser user,
                                                                                       int serviceDeskId,
                                                                                       Collection<FilePart> fileParts,
                                                                                       HttpServletRequest request) {
        return Steps
            .begin(validateAttachmentTarget(user, serviceDeskId))
            .then(target -> canCreateTemporaryAttachment(user, target))
            .then((target, permission) -> createTemporaryAttachments(user, target, fileParts, getOrCreateFormToken(request)))
            .yield((target, permission, result) -> result);

    }

    /**
     * {@link TemporaryWebAttachmentManager} expects either an {@link Issue} or {@link Project} as the target of the attachment
     */
    private Either<AnError, Either<Issue, Project>> validateAttachmentTarget(ApplicationUser user,
                                                                             int serviceDeskId) {
        return Steps
            .begin(serviceDeskService.getServiceDeskById(user, serviceDeskId))
            .yield(sd -> Either.right(projectManager.getProjectObj(sd.getProjectId())));
    }

    /**
     * {@link XsrfTokenGenerator#generateToken()} gets the token from the current request, generating a new one if none is found.
     * <p>
     * This is used as form token for the user, which Jira expects to associated the temporary attachments to, for its cleaning up purpose.
     * See default implementation of {@link TemporaryWebAttachmentManager} at DefaultTemporaryWebAttachmentManager.java
     * <p>
     * Re: XSRF check, since atlassian-rest v2.4, the default checking by the atlassian-rest platform is performed at {@link XsrfResourceFilter}
     * which is to say, at this point of invocation, the XSRF checking was already performed.
     */
    private String getOrCreateFormToken(HttpServletRequest request) {
        return xsrfGenerator.generateToken(request);
    }

    private Either<AnError, CreateTemporaryWebAttachmentResultDTO> createTemporaryAttachments(ApplicationUser user,
                                                                                              Either<Issue, Project> attachmentTarget,
                                                                                              Collection<FilePart> fileParts,
                                                                                              String atl_token) {
        if (!Optional.ofNullable(fileParts).isPresent() || fileParts.isEmpty()) {
            return Either.left(errorResultHelper.badRequest400("sd.attachment.empty.error").build());
        }

        List<Either<AnError, TemporaryWebAttachmentDTO>> result = fileParts.stream()
                .map(createTemporaryAttachment(user, attachmentTarget, atl_token))
                .collect(Collectors.toList());

        Iterator<AnError> errors = Eithers.filterLeft(result).iterator();
        if (errors.hasNext()) {
            return Either.left(errors.next());
        } else {
            List<TemporaryWebAttachmentDTO> attachments = Lists.newArrayList(Eithers.filterRight(result));
            return Either.right(new CreateTemporaryWebAttachmentResultDTO(attachments));
        }
    }

    /**
     * All validations are performed in {@link TemporaryWebAttachmentManager#createTemporaryWebAttachment},
     * including the permission or any of attachment enable configuration, attachment size and file name.
     * <p>
     * This snippet solely aims to translate error code to a 403 on permission concern.
     */
    private Either<AnError, Boolean> canCreateTemporaryAttachment(ApplicationUser user,
                                                                  Either<Issue, Project> attachmentTarget) {
        if (!attachmentValidator.canCreateTemporaryAttachments(user, attachmentTarget, new SimpleErrorCollection())) {
            return Either.left(errorResultHelper.forbidden403("sd.attachment.permission.error").build());
        } else {
            return Either.right(true);
        }
    }

    private Function<FilePart, Either<AnError, TemporaryWebAttachmentDTO>> createTemporaryAttachment(
        ApplicationUser user,
        Either<Issue, Project> attachmentTarget,
        String atl_token) {
        return filePart -> Steps
            .begin(toTempFile(filePart))
            .then(file -> createTemporaryAttachment(user,
                attachmentTarget,
                atl_token,
                file,
                getFilename(filePart),
                StringUtils.defaultString(filePart.getContentType(), "application/octet-stream")))
            .yield((file, dto) -> dto);
    }

    private Either<AnError, TemporaryWebAttachmentDTO> createTemporaryAttachment(ApplicationUser user,
                                                                                 Either<Issue, Project> attachmentTarget,
                                                                                 String formToken,
                                                                                 File tempFile,
                                                                                 String fileName,
                                                                                 String contentType) {
        try {
            long sizeInByte = tempFile.length();
            try (InputStream inputStream = new FileInputStream(tempFile)) {
                return temporaryWebAttachmentManager.createTemporaryWebAttachment(inputStream, fileName, contentType, sizeInByte, attachmentTarget, formToken, user)
                    .bimap(
                        toAnError(),
                        temporaryWebAttachmentDTOFactory.toDTO()
                    );
            }
        } catch (IOException ioe) {
            return Either.left(errorResultHelper.internalServiceError500("sd.attachment.create.error", ioe.getLocalizedMessage()).build());
        }
    }

    private Function<AttachmentError, AnError> toAnError() {
        return attachmentError -> errorResultHelper.anError(attachmentError.getReason().getHttpStatusCode(), "sd.attachment.create.error", attachmentError.getLocalizedMessage());
    }

    /**
     * Strip the local file system path from the file name.
     * <p>
     * See {@link FilePart#getName} then {@link CommonsFileUploadFilePart#getName} then {@link FileItem#getName}
     * which explicitly documents that it sometimes yields client-system full path.
     */
    private String getFilename(FilePart filePart) {
        //Because File separator is platform dependent, we have to escape it properly.
        String[] parts = filePart.getName().split(Pattern.quote(File.separator));
        return parts[parts.length - 1];
    }

    /**
     * The idea is shamelessly ported from Jira's IssueAttachmentsResource: convert {@link FilePart} into {@link File} to get its size.
     * <p>
     * {@link TemporaryWebAttachmentManager#createTemporaryWebAttachment} expects an {@link InputStream} and its <code>size</code>,
     * in order to validate {@link InputStream}'s length.
     * {@link FilePart} is a wrapper on Apache Commons File Upload, and it encapsulates the {@link FileItem#getSize} well.
     */
    private Either<AnError, File> toTempFile(FilePart filePart) {
        try {
            File file = File.createTempFile("jsd-attachment-", ".tmp");
            file.deleteOnExit();
            filePart.write(file);
            return Either.right(file);
        } catch (IOException ioe) {
            return Either.left(errorResultHelper.badRequest400("sd.attachment.create.error", ioe.getLocalizedMessage()).build());
        }
    }
}
