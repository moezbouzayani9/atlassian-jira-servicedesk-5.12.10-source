import $ from 'servicedesk/jQuery';
import AjsMeta from 'jira/util/data/meta';
import JiraProjectsApi from 'jira/api/projects';

const SERVICE_DESK_PROJECT_KEY = 'service_desk';

export const isSDProject = function () {
    // Three ways to get the project type but they are not always available depending
    // on which page are we in or order of things getting loaded on the page.
    // If one way doesn't give us the project type, fallback onto the next one.
    const projectType =
        $('#dnd-metadata-webpanel').data('project-type') ||
        AjsMeta.get('issue-project-type') ||
        JiraProjectsApi.getCurrentProjectType();

    return projectType === SERVICE_DESK_PROJECT_KEY;
};

export const isAgent = function () {
    return $('.sd-comment-container').data('is-agent');
};

export const isSecurityLevelAvailable = function () {
    return $('.sd-comment-container').data('is-security-level-available');
};
