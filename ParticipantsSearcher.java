package com.atlassian.servicedesk.squalor.customfields;

import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.customfields.CustomFieldSearcher;
import com.atlassian.jira.issue.customfields.SortableCustomFieldSearcher;
import com.atlassian.jira.issue.customfields.converters.UserConverter;
import com.atlassian.jira.issue.customfields.searchers.CustomFieldSearcherClauseHandler;
import com.atlassian.jira.issue.customfields.searchers.UserPickerGroupSearcher;
import com.atlassian.jira.issue.customfields.searchers.transformer.CustomFieldInputHelper;
import com.atlassian.jira.issue.customfields.statistics.CustomFieldStattable;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.search.LuceneFieldSorter;
import com.atlassian.jira.issue.search.searchers.information.SearcherInformation;
import com.atlassian.jira.issue.search.searchers.renderer.SearchRenderer;
import com.atlassian.jira.issue.search.searchers.transformer.SearchInputTransformer;
import com.atlassian.jira.issue.statistics.StatisticsMapper;
import com.atlassian.jira.jql.operand.JqlOperandResolver;
import com.atlassian.jira.jql.resolver.UserResolver;
import com.atlassian.jira.plugin.customfield.CustomFieldSearcherModuleDescriptor;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.template.VelocityTemplatingEngine;
import com.atlassian.jira.user.UserFilterManager;
import com.atlassian.jira.user.UserHistoryManager;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.util.EmailFormatter;
import com.atlassian.jira.util.velocity.VelocityRequestContextFactory;
import com.atlassian.jira.web.FieldVisibilityManager;
import javax.annotation.Nonnull;

/**
 * We need to extend the UserPickerGroupSearcher because some components are internal to JIRA and we have
 * to access them with ComponentAccessor (can't be auto-wired from our plugin)
 */
public class ParticipantsSearcher implements CustomFieldSearcher, SortableCustomFieldSearcher, CustomFieldStattable {
    private final UserPickerGroupSearcher userPickerGroupSearcher;

    public ParticipantsSearcher() {
        this.userPickerGroupSearcher = new UserPickerGroupSearcher(
            ComponentAccessor.getComponent(UserConverter.class),
            ComponentAccessor.getComponent(JiraAuthenticationContext.class),
            ComponentAccessor.getComponent(VelocityRequestContextFactory.class),
            ComponentAccessor.getComponent(VelocityTemplatingEngine.class),
            ComponentAccessor.getComponent(ApplicationProperties.class),
            ComponentAccessor.getComponent(UserSearchService.class),
            ComponentAccessor.getComponent(FieldVisibilityManager.class),
            ComponentAccessor.getComponent(JqlOperandResolver.class),
            ComponentAccessor.getComponent(UserResolver.class),
            ComponentAccessor.getComponent(UserManager.class),
            ComponentAccessor.getComponent(CustomFieldInputHelper.class),
            ComponentAccessor.getComponent(GroupManager.class),
            ComponentAccessor.getComponent(PermissionManager.class),
            ComponentAccessor.getComponent(UserHistoryManager.class),
            ComponentAccessor.getComponent(UserFilterManager.class),
            ComponentAccessor.getComponent(EmailFormatter.class));
    }

    @Override
    public void init(CustomFieldSearcherModuleDescriptor customFieldSearcherModuleDescriptor) {
        userPickerGroupSearcher.init(customFieldSearcherModuleDescriptor);
    }

    @Override
    public CustomFieldSearcherModuleDescriptor getDescriptor() {
        return userPickerGroupSearcher.getDescriptor();
    }

    @Override
    public CustomFieldSearcherClauseHandler getCustomFieldSearcherClauseHandler() {
        return userPickerGroupSearcher.getCustomFieldSearcherClauseHandler();
    }

    @Nonnull
    @Override
    public LuceneFieldSorter getSorter(CustomField customField) {
        return userPickerGroupSearcher.getSorter(customField);
    }

    @Override
    public StatisticsMapper getStatisticsMapper(CustomField customField) {
        return userPickerGroupSearcher.getStatisticsMapper(customField);
    }

    @Override
    public void init(CustomField customField) {
        userPickerGroupSearcher.init(customField);
    }

    @Override
    public SearcherInformation<CustomField> getSearchInformation() {
        return userPickerGroupSearcher.getSearchInformation();
    }

    @Override
    public SearchInputTransformer getSearchInputTransformer() {
        return userPickerGroupSearcher.getSearchInputTransformer();
    }

    @Override
    public SearchRenderer getSearchRenderer() {
        return userPickerGroupSearcher.getSearchRenderer();
    }
}