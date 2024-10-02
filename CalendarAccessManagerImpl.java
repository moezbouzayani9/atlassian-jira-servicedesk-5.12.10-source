package com.atlassian.jira.plugins.workinghours.internal.calendar.access;

import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.plugins.workinghours.api.calendar.Calendar;
import com.atlassian.jira.plugins.workinghours.api.calendar.CalendarInfo;
import com.atlassian.jira.plugins.workinghours.api.calendar.access.CalendarAccessManager;
import com.atlassian.jira.plugins.workinghours.api.calendar.access.Operation;
import com.atlassian.jira.plugins.workinghours.spi.calendar.access.Outcome;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.SimpleErrorCollection;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.google.common.collect.Sets;
import io.atlassian.fugue.Either;
import io.atlassian.fugue.Option;
import io.atlassian.fugue.Unit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.atlassian.jira.plugins.workinghours.spi.calendar.access.Outcome.ABSTAIN;
import static com.atlassian.jira.plugins.workinghours.spi.calendar.access.Outcome.NO;
import static com.atlassian.jira.plugins.workinghours.spi.calendar.access.Outcome.YES;
import static com.atlassian.pocketknife.api.util.ServiceResult.error;
import static com.atlassian.pocketknife.api.util.ServiceResult.ok;
import static io.atlassian.fugue.Either.left;
import static io.atlassian.fugue.Either.right;
import static io.atlassian.fugue.Unit.Unit;
import static java.util.stream.Collectors.toList;

/**
 * Encapsulates the permission spi
 */
@Component
@ExportAsService
public class CalendarAccessManagerImpl implements CalendarAccessManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CalendarAccessManager.class);

    @Autowired
    private GlobalPermissionManager globalPermissionManager;

    @Autowired
    private PluginAccessor pluginAccessor;

    /**
     * Get all conditions applicable for a given project
     */
    public Either<ErrorCollection, Option<Object>> collectValidate(ApplicationUser user,
                                                                   Calendar calendar,
                                                                   Operation operation) {
        List<CalendarAccessModuleDescriptor> moduleDescriptors = pluginAccessor.getEnabledModuleDescriptorsByClass(CalendarAccessModuleDescriptor.class);

        SimpleErrorCollection result = new SimpleErrorCollection();
        for (CalendarAccessModuleDescriptor moduleDescriptor : moduleDescriptors) {
            try {
                Either<ErrorCollection, Option<Object>> validationErrors = moduleDescriptor.getModule().validate(user, calendar, operation);
                if (validationErrors.isLeft()) {
                    result.addErrorCollection(validationErrors.left().get());
                }
            } catch (Exception e) {
                LOGGER.debug("Plugin module {} threw exception {} while executing validateDelete", moduleDescriptor.getCompleteKey(), e.getMessage());
            }
        }

        if (result.hasAnyErrors()) {
            return error(result);
        } else {
            return ok();
        }
    }

    @Override
    public List<String> getOperationMessages(ApplicationUser user, Calendar calendar, Operation operation) {
        return handleGetOperationMessages(user, calendar, operation);
    }

    @Override
    public boolean hasPermission(ApplicationUser user, Map<String, String> context, Operation operation) {
        Outcome outcome = hasPermissionDelegated(user, context, operation);
        return handleOutcomeForPermissioning(user, outcome);
    }

    @Override
    public boolean matchesFilter(ApplicationUser user, CalendarInfo info, Map<String, String> filter) {
        // empty filter matches everything
        if (filter == null || filter.isEmpty()) {
            return true;
        }

        // ask
        Outcome outcome = matchFilterDelegated(user, info, filter);
        return handleOutcomeForFiltering(outcome);
    }

    @Override
    public Either<ErrorCollection, Option<Object>> validate(ApplicationUser user,
                                                            Calendar calendar,
                                                            Operation operation) {
        return collectValidate(user, calendar, operation);
    }

    @Override
    public Either<Map<String, ErrorCollection>, Unit> validateForPluginsOnly(ApplicationUser user,
                                                                             Calendar calendar,
                                                                             Operation operation) {
        return collectValidateForPluginsOnly(user, calendar, operation);
    }

    /**
     * Get all validation conditions excluding our service project implementation.
     * Returns a map of the plugin key and error collection.
     */
    private Either<Map<String, ErrorCollection>, Unit> collectValidateForPluginsOnly(ApplicationUser user,
                                                                                     Calendar calendar,
                                                                                     Operation operation) {
        final List<CalendarAccessModuleDescriptor> moduleDescriptors = pluginAccessor.getEnabledModuleDescriptorsByClass(CalendarAccessModuleDescriptor.class)
            .stream()
            .filter(md -> !"servicedesk-calendar-access".equals(md.getKey()))
            .collect(toList());

        final Map<String, ErrorCollection> resultMap = new HashMap<>();

        for (CalendarAccessModuleDescriptor moduleDescriptor : moduleDescriptors) {
            try {
                moduleDescriptor.getModule().validate(user, calendar, operation)
                    .leftMap(errorCollection -> {
                        resultMap.put(moduleDescriptor.getKey(), errorCollection);
                        return errorCollection;
                    });
            } catch (Exception e) {
                LOGGER.debug("Plugin module {} threw exception {} while executing validateDelete", moduleDescriptor.getCompleteKey(), e.getMessage());
            }
        }

        if (resultMap.isEmpty()) {
            return right(Unit());
        } else {
            return left(resultMap);
        }
    }

    private boolean fallbackPermissionCheck(ApplicationUser user) {
        return globalPermissionManager.hasPermission(GlobalPermissionKey.ADMINISTER, user);
    }

    // Gets the update message from each implementation of CalendarAccess and appends them together.
    private List<String> handleGetOperationMessages(ApplicationUser user, Calendar calendar, Operation operation) {
        List<CalendarAccessModuleDescriptor> moduleDescriptors = pluginAccessor.getEnabledModuleDescriptorsByClass(CalendarAccessModuleDescriptor.class);

        List<String> operationMessage = new ArrayList<String>();

        for (CalendarAccessModuleDescriptor moduleDescriptor : moduleDescriptors) {
            try {
                List<String> response = moduleDescriptor.getModule().getOperationMessages(user, calendar, operation);

                if (response != null) {
                    operationMessage.addAll(response);
                }
            } catch (Exception e) {
                LOGGER.debug("Plugin module {} threw exception {} while executing getOperationMessages", moduleDescriptor.getCompleteKey(), e.getMessage());
            }
        }

        return operationMessage;
    }

    private boolean handleOutcomeForFiltering(Outcome outcome) {
        if (outcome == ABSTAIN) {
            return false;
        } else if (outcome == YES) {
            return true;
        } else {
            return false;
        }
    }

    private boolean handleOutcomeForPermissioning(ApplicationUser user, Outcome outcome) {
        if (outcome == ABSTAIN) {
            return fallbackPermissionCheck(user);
        } else if (outcome == YES) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get all conditions applicable for a given project
     */
    private Outcome hasPermissionDelegated(ApplicationUser user, Map<String, String> context, Operation operation) {
        List<CalendarAccessModuleDescriptor> moduleDescriptors = pluginAccessor.getEnabledModuleDescriptorsByClass(CalendarAccessModuleDescriptor.class);

        Set<Outcome> results = Sets.newHashSetWithExpectedSize(3);
        for (CalendarAccessModuleDescriptor moduleDescriptor : moduleDescriptors) {
            try {
                Outcome outcome = moduleDescriptor.getModule().hasPermission(user, context, operation);
                if (outcome != ABSTAIN) {
                    results.add(outcome);
                }
            } catch (Exception e) {
                LOGGER.debug("Plugin module {} threw exception {} while executing hasPermission", moduleDescriptor.getCompleteKey(), e.getMessage());
            }
        }

        return yesThenNo(results);
    }

    /**
     * Get all conditions applicable for a given project
     */
    private Outcome matchFilterDelegated(ApplicationUser user, CalendarInfo info, Map<String, String> filter) {
        List<CalendarAccessModuleDescriptor> moduleDescriptors = pluginAccessor.getEnabledModuleDescriptorsByClass(CalendarAccessModuleDescriptor.class);

        Set<Outcome> results = Sets.newHashSetWithExpectedSize(3);
        for (CalendarAccessModuleDescriptor moduleDescriptor : moduleDescriptors) {
            try {
                Outcome outcome = moduleDescriptor.getModule().matchesFilter(user, info, filter);
                if (outcome != ABSTAIN) {
                    results.add(outcome);
                }
            } catch (Exception e) {
                LOGGER.debug("Plugin module {} threw exception {} while executing matchesFilter", moduleDescriptor.getCompleteKey(), e.getMessage());
            }
        }

        // rather filter
        return noThenYes(results);
    }

    private Outcome noThenYes(Set<Outcome> results) {
        if (results.isEmpty()) {
            return ABSTAIN;
        }
        if (results.contains(NO)) {
            return NO;
        } else {
            return YES;
        }
    }

    private Outcome yesThenNo(Set<Outcome> results) {
        if (results.isEmpty()) {
            return ABSTAIN;
        }
        if (results.contains(YES)) {
            return YES;
        } else {
            return NO;
        }
    }

}
