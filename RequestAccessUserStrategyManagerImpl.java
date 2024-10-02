package com.atlassian.servicedesk.internal.permission.security;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.servicedesk.api.user.CheckedUser;
import com.atlassian.servicedesk.internal.spi.permission.security.CustomerInvolvedType;
import com.atlassian.servicedesk.internal.spi.permission.security.RequestAccessUserProviderRegistry;
import com.atlassian.servicedesk.internal.spi.permission.security.RequestAccessUserStrategy;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

@Component
@ExportAsService
class RequestAccessUserStrategyManagerImpl implements RequestAccessUserStrategyManager, RequestAccessUserProviderRegistry {

    /**
     * Holds all registered strategies
     * <p>
     * Note: We are using a linked hash set here to ensure that our registered strategies are executed first.
     */
    private final LinkedHashSet<RequestAccessUserStrategy> strategies = new LinkedHashSet<>();

    @Autowired
    public RequestAccessUserStrategyManagerImpl(
        final ReporterRequestAccessUserStrategy reporterRequestAccessUserStrategy,
        final RequestParticipantRequestAccessUserStrategy requestParticipantRequestAccessUserStrategy,
        final CustomerOrganisationParticipantRequestAccessUserStrategy customerOrganisationParticipantRequestAccessUserStrategy,
        final CustomerGroupParticipantRequestAccessUserStrategy customerGroupParticipantRequestAccessUserStrategy,
        final CustomerOutsiderRequestAccessUserStrategy customerOutsiderRequestAccessUserStrategy
    ) {
        // register reporter strategy
        register(reporterRequestAccessUserStrategy);

        // then request participant strategy
        register(requestParticipantRequestAccessUserStrategy);

        // then customer group participant strategy
        register(customerGroupParticipantRequestAccessUserStrategy);

        // then customer organisation participant strategy
        register(customerOrganisationParticipantRequestAccessUserStrategy);

        // we also register the customer outsider strategy
        register(customerOutsiderRequestAccessUserStrategy);
    }

    // API

    @Override
    public List<CheckedUser> getMembers(final Issue issue) {
        return getMembersFromStrategyStream(issue, strategies.stream());
    }

    @Override
    public List<CheckedUser> getMembersForTypes(final Issue issue, final CustomerInvolvedType... types) {
        return getMembersFromStrategyStream(issue, getStrategyStreamForTypes(types));
    }

    private Stream<RequestAccessUserStrategy> getStrategyStreamForTypes(final CustomerInvolvedType[] types) {
        final Set<CustomerInvolvedType> typesSet = EnumSet.copyOf(Arrays.asList(types));
        return strategies.stream().filter(strategy -> typesSet.contains(strategy.getType()));
    }

    private List<CheckedUser> getMembersFromStrategyStream(final Issue issue,
                                                           final Stream<RequestAccessUserStrategy> strategies) {
        final List<CheckedUser> members = strategies
            .flatMap(strategy -> strategy.getUsers(issue).stream())
            .distinct()
            .collect(toList());
        return unmodifiableList(members);
    }

    @Override
    public boolean match(final ApplicationUser user, final Issue issue) {
        // the user must matches at least one strategy type
        return strategies.stream().anyMatch(strategy -> strategy.match(user, issue));
    }

    @Override
    public boolean match(final ApplicationUser user, final Issue issue, final CustomerInvolvedType... type) {
        return getStrategyStreamForTypes(type).anyMatch(strategy -> strategy.match(user, issue));
    }

    // SPI

    @Override
    public void register(final RequestAccessUserStrategy strategy) {
        strategies.add(strategy);
    }

    @Override
    public void unregister(final RequestAccessUserStrategy strategy) {
        strategies.remove(strategy);
    }
}
