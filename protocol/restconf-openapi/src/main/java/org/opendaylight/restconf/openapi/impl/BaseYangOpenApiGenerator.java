/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URI;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.model.DocumentEntity;
import org.opendaylight.restconf.openapi.model.MetadataEntity;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;

public abstract class BaseYangOpenApiGenerator {
    private static final String CONTROLLER_RESOURCE_NAME = "Controller";
    public static final List<Map<String, List<String>>> SECURITY = List.of(Map.of("basicAuth", List.of()));

    private final DOMSchemaService schemaService;

    protected BaseYangOpenApiGenerator(final @NonNull DOMSchemaService schemaService) {
        this.schemaService = requireNonNull(schemaService);
    }

    private @NonNull EffectiveModelContext modelContext() {
        return schemaService.getGlobalContext();
    }

    public DocumentEntity getControllerModulesDoc(final URI uri, final int width, final int depth,
            final int offset, final int limit) throws IOException {
        final var modelContext = modelContext();
        final var schema = createSchemaFromUri(uri);
        final var host = createHostFromUri(uri);
        final var title = "Controller modules of RESTCONF";
        final var url = schema + "://" + host + "/";
        final var basePath = getBasePath();
        final var modulesWithoutDuplications = getModulesWithoutDuplications(modelContext);
        final var portionOfModels = getModelsSublist(modulesWithoutDuplications, offset, limit);
        return new DocumentEntity(modelContext, title, url, SECURITY, CONTROLLER_RESOURCE_NAME, "", false, true,
            portionOfModels, basePath, width, depth);
    }

    public MetadataEntity getControllerModulesMeta(final int offset, final int limit) throws IOException {
        final var modulesWithoutDuplications = getModulesWithoutDuplications(modelContext());
        return new MetadataEntity(offset, limit, modulesWithoutDuplications.size(),
            configModulesList(modulesWithoutDuplications).size());
    }

    public DocumentEntity getApiDeclaration(final String module, final String revision, final URI uri, final int width,
            final int depth) throws IOException {
        return getApiDeclaration(module, revision, uri, modelContext(), "", CONTROLLER_RESOURCE_NAME, width, depth);
    }

    public DocumentEntity getApiDeclaration(final String moduleName, final String revision, final URI uri,
            final EffectiveModelContext modelContext, final String urlPrefix, final @NonNull String deviceName,
            final int width, final int depth) throws IOException {
        final Optional<Revision> rev;

        try {
            rev = Revision.ofNullable(revision);
        } catch (final DateTimeParseException e) {
            throw new IOException("Failed to parse revision", e);
        }

        final var module = modelContext.findModule(moduleName, rev).orElseThrow(
            () -> new IOException("Could not find module by name,revision: " + moduleName + "," + revision));

        final var schema = createSchemaFromUri(uri);
        final var host = createHostFromUri(uri);
        final var title = module.getName();
        final var url = schema + "://" + host + "/";
        final var basePath = getBasePath();
        final var modules = List.of(module);
        return new DocumentEntity(modelContext, title, url, SECURITY, deviceName, urlPrefix, true, false,
            modules, basePath, width, depth);
    }

    public String createHostFromUri(final URI uri) {
        String portPart = "";
        final int port = uri.getPort();
        if (port != -1) {
            portPart = ":" + port;
        }
        return uri.getHost() + portPart;
    }

    public String createSchemaFromUri(final URI uri) {
        return uri.getScheme();
    }

    public abstract String getBasePath();

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

    public static Collection<? extends Module> getModelsSublist(final List<Module> modulesWithoutDuplications,
            final int offset, final int limit) {
        if (offset < 0 || limit < 0) {
            return List.of();
        }
        if (offset == 0 && limit == 0) {
            return modulesWithoutDuplications;
        }

        final var modules = configModulesList(modulesWithoutDuplications);
        final var size = modules.size();
        return offset > size ? List.of() : modules.subList(offset, limit == 0 ? size : Math.min(size, offset + limit));
    }

    public static List<Module> configModulesList(final List<Module> modulesWithoutDuplications) {
        return modulesWithoutDuplications.stream()
            .filter(module -> !module.getRpcs().isEmpty() || module.getChildNodes().stream()
                .anyMatch(node -> node instanceof ContainerSchemaNode || node instanceof ListSchemaNode))
            .toList();
    }
}
