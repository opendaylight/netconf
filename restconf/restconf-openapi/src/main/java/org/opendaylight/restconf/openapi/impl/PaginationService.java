/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;

/**
 * This class provides a pagination service for modules. It is used to paginate modules in the OpenAPI generator, and it
 * provides metadata for the pagination.
 */
public final class PaginationService {
    // FIXME these fields need explanation
    private static final String TOTAL_MODULES = "totalModules";
    private static final String CONFIG_MODULES = "configModules";
    private static final String NON_CONFIG_MODULES = "nonConfigModules";
    private static final String CURRENT_PAGE = "currentPage";
    private static final String TOTAL_PAGES = "totalPages";

    private final Map<String, Object> metadata;
    private final Collection<? extends Module> modules;

    /**
     * Create a new instance of PaginationService for limited number of modules.
     *
     * @param modelContext EffectiveModelContext.
     * @param offset The offset.
     * @param limit The limit.
     */
    public PaginationService(final EffectiveModelContext modelContext, final int offset, final int limit) {
        metadata = new HashMap<>();
        if (limit == 0 && offset == 0) {
            this.modules = getAllModules(modelContext, metadata);
        } else {
            this.modules = getModelsSublist(modelContext, offset, limit, metadata);
        }
    }

    /**
     * Create a new instance of PaginationService for single Module.
     *
     * @param module The module.
     */
    public PaginationService(final @NonNull Module module) {
        metadata = new HashMap<>();
        this.modules = List.of(module);
        fillMetadataForSingleModule(module, metadata);
    }

    /**
     * Create a new instance of PaginationService for all modules.
     *
     * @param modelContext EffectiveModelContext.
     */
    public PaginationService(final @NonNull EffectiveModelContext modelContext) {
        metadata = new HashMap<>();
        this.modules = getAllModules(modelContext, metadata);
    }

    private static Collection<? extends Module> getModelsSublist(final EffectiveModelContext modelContext,
            final int offset, final int limit, final Map<String, Object> metadata) {
        final var augmentingModules = new ArrayList<Module>();
        final var modules = modulesList(getModulesWithoutDuplications(modelContext), augmentingModules);
        final var modulesSize = modules.size();
        if (offset > modulesSize || offset < 0 || limit < 0) {
            fillMetadataForPortionOfModules(offset, limit, augmentingModules.size(), modulesSize, metadata);
            return List.of();
        } else {
            final var end = limit == 0 ? modulesSize : Math.min(modulesSize, offset + limit);
            final var portionOfModules = modules.subList(offset, end);
            fillMetadataForPortionOfModules(offset, limit, augmentingModules.size(), modulesSize, metadata);
            return portionOfModules;
        }
    }

    private static Collection<? extends Module> getAllModules(final EffectiveModelContext modelContext,
            final Map<String, Object> metadata) {
        final var modulesWithoutDuplications = getModulesWithoutDuplications(modelContext);
        final var augmentingModules = new ArrayList<Module>();
        final var modules = modulesList(modulesWithoutDuplications, augmentingModules);
        fillMetadataForAllModules(augmentingModules.size(), modules.size(), metadata);
        return modulesWithoutDuplications;
    }

    private static void fillMetadataForPortionOfModules(final Integer offset, final Integer limit,
            final Integer nonConfigModels, final Integer configModels, final Map<String, Object> metadata) {
        metadata.put(TOTAL_MODULES, nonConfigModels + configModels);
        metadata.put(CONFIG_MODULES, configModels);
        metadata.put(NON_CONFIG_MODULES, nonConfigModels);
        metadata.put("limit", limit);
        metadata.put("offset", offset);
        metadata.put(CURRENT_PAGE, offset / limit + 1);
        metadata.put(TOTAL_PAGES, configModels / limit + 1);
        metadata.put("previousOffset", Math.max(offset - limit, 0));
        metadata.put("nextOffset", Math.min(offset + limit, configModels));
    }

    private static void fillMetadataForSingleModule(final Module module, final Map<String, Object> metadata) {
        final var isModuleConfig = containsDataOrOperation(module);
        metadata.put(TOTAL_MODULES, 1);
        metadata.put(CONFIG_MODULES, isModuleConfig ? 1 : 0);
        metadata.put(NON_CONFIG_MODULES, isModuleConfig ? 0 : 1);
        metadata.put(CURRENT_PAGE, 1);
        metadata.put(TOTAL_PAGES, 1);
    }

    private static void fillMetadataForAllModules(final Integer nonConfigModules, final Integer configModules,
            final Map<String, Object> metadata) {
        metadata.put(TOTAL_MODULES, configModules + nonConfigModules);
        metadata.put(CONFIG_MODULES, configModules);
        metadata.put(NON_CONFIG_MODULES, nonConfigModules);
        metadata.put(CURRENT_PAGE, 1);
        metadata.put(TOTAL_PAGES, 1);
    }

    public static List<Module> getModulesWithoutDuplications(final @NonNull EffectiveModelContext modelContext) {
        return List.copyOf(modelContext.getModules()
            .stream()
            .sorted(Comparator.comparing(Module::getName))
            .collect(Collectors.toMap(
                Module::getName,
                Function.identity(),
                (module1, module2) -> Revision.compare(
                    module1.getRevision(), module2.getRevision()) > 0 ? module1 : module2,
                LinkedHashMap::new))
            .values());
    }

    private static List<Module> modulesList(final List<Module> modulesWithoutDuplications,
            final List<Module> augmentingModules) {
        return modulesWithoutDuplications
            .stream()
            .filter(module -> {
                if (containsDataOrOperation(module)) {
                    return true;
                } else {
                    augmentingModules.add(module);
                    return false;
                }
            })
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private static boolean containsDataOrOperation(final Module module) {
        return !module.getRpcs().isEmpty() || module.getChildNodes()
            .stream()
            .anyMatch(node -> node instanceof ContainerSchemaNode || node instanceof ListSchemaNode);
    }

    public @NonNull Collection<? extends Module> getModules() {
        return modules;
    }

    public @NonNull Map<String, Object> getMetadata() {
        return metadata;
    }
}
