/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;

/**
 * This class provides a pagination service for modules. It is used to paginate modules in the OpenAPI generator, and it
 * provides metadata for the pagination.
 */
public final class PaginationService {
    private static final String TOTAL_MODULES = "totalModules";
    private static final String CONFIG_MODULES = "configModules";
    private static final String NON_CONFIG_MODULES = "nonConfigModules";
    private static final String CURRENT_PAGE = "currentPage";
    private static final String TOTAL_PAGES = "totalPages";

    private final Map<String, Object> metadata;
    private final Collection<? extends Module> modules;
    private final boolean isForAllModules;

    /**
     * Create a new instance of PaginationService for limited number of modules.
     *
     * @param context The effective model context.
     * @param offset The offset.
     * @param limit The limit.
     */
    public PaginationService(final @NonNull EffectiveModelContext context, final @Nullable Integer offset,
            final @Nullable Integer limit) {
        metadata = new HashMap<>();
        requireNonNull(context);
        if (forAllModules(offset, limit)) {
            this.isForAllModules = true;
            this.modules = getAllModules(context, metadata);
        } else {
            this.isForAllModules = false;
            this.modules = getPortionOfModels(context, offset, limit, metadata);
        }
    }

    /**
     * Create a new instance of PaginationService for single Module.
     *
     * @param module The module.
     */
    public PaginationService(final @NonNull Module module) {
        metadata = new HashMap<>();
        this.modules = List.of(requireNonNull(module));
        this.isForAllModules = false;
        fillMetadataForSingleModule(module, metadata);
    }

    /**
     * Create a new instance of PaginationService for all modules.
     *
     * @param context The effective model context.
     */
    public PaginationService(final @NonNull EffectiveModelContext context) {
        metadata = new HashMap<>();
        this.modules = getAllModules(requireNonNull(context), metadata);
        this.isForAllModules = true;
    }

    private static Collection<? extends Module> getPortionOfModels(final EffectiveModelContext context,
            final Integer offset, final Integer limit, final Map<String, Object> metadata) {
        final var augmentingModules = new ArrayList<Module>();
        final var modules = modulesList(context, augmentingModules);
        final var modulesSize = modules.size();
        final var start = offset == null || offset < 0 ? 0 : offset;
        final var end = limit == null || limit <= 0 ? modulesSize : Math.min(modulesSize, start + limit);
        final var portionOfModules = start > modules.size() ? new ArrayList<Module>() : modules.subList(start, end);
        portionOfModules.addAll(augmentingModules);
        fillMetadataForPortionOfModules(offset, limit, augmentingModules.size(), modulesSize, metadata);
        return portionOfModules;
    }

    private static Collection<? extends Module> getAllModules(final EffectiveModelContext context,
            final Map<String, Object> metadata) {
        final var augmentingModules = new ArrayList<Module>();
        final var modules = modulesList(context, augmentingModules);
        final var modulesSize = modules.size();
        fillMetadataForAllModules(augmentingModules.size(), modulesSize, metadata);
        return context.getModules();
    }

    private static boolean forAllModules(final Integer offset, final Integer limit) {
        if (offset == null && limit == null) {
            return true;
        } else if (offset == null || limit == null) {
            return false;
        }
        return offset == 0 && limit == 0;
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

    private static List<Module> modulesList(final EffectiveModelContext context, final List<Module> augmentingModules) {
        final var modulesWithListOrContainer = new ArrayList<Module>();
        context.getModules().forEach(module -> {
            if (containsDataOrOperation(module)) {
                modulesWithListOrContainer.add(module);
            } else {
                augmentingModules.add(module);
            }
        });
        return modulesWithListOrContainer;
    }

    private static boolean containsDataOrOperation(final Module module) {
        if (!module.getRpcs().isEmpty()) {
            return true;
        }
        for (final var child : module.getChildNodes()) {
            if (child instanceof ListSchemaNode || child instanceof ContainerSchemaNode) {
                return true;
            }
        }
        return false;
    }

    public @NonNull Collection<? extends Module> getModules() {
        return modules;
    }

    public @NonNull Map<String, Object> getMetadata() {
        return metadata;
    }

    public boolean isForAllModules() {
        return isForAllModules;
    }
}
