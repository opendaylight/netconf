/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
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

    public OpenApiInputStream getControllerModulesDoc(final UriInfo uriInfo, final Integer width, final Integer depth,
            final Integer offset, final Integer limit) throws IOException {
        final var modelContext = requireNonNull(schemaService.getGlobalContext());
        final var schema = createSchemaFromUriInfo(uriInfo);
        final var host = createHostFromUriInfo(uriInfo);
        final var title = "Controller modules of RESTCONF";
        final var url = schema + "://" + host + "/";
        final var basePath = getBasePath();
        final var modulesWithoutDuplications = getModulesWithoutDuplications(modelContext);
        final var portionOfModels = getModelsSublist(modulesWithoutDuplications, requireNonNullElse(offset, 0),
            requireNonNullElse(limit, 0));
        return new OpenApiInputStream(modelContext, title, url, SECURITY, CONTROLLER_RESOURCE_NAME, "", false, true,
            portionOfModels, basePath, width, depth);
    }

    public OpenApiInputStream getApiDeclaration(final String module, final String revision, final UriInfo uriInfo,
            final Integer width, final Integer depth) throws IOException {
        final var modelContext = schemaService.getGlobalContext();
        Preconditions.checkState(modelContext != null);
        return getApiDeclaration(module, revision, uriInfo, modelContext, "", CONTROLLER_RESOURCE_NAME, width, depth);
    }

    public OpenApiInputStream getApiDeclaration(final String moduleName, final String revision, final UriInfo uriInfo,
            final EffectiveModelContext modelContext, final String urlPrefix, final @NonNull String deviceName,
            final Integer width, final Integer depth) throws IOException {
        final Optional<Revision> rev;

        try {
            rev = Revision.ofNullable(revision);
        } catch (final DateTimeParseException e) {
            throw new IllegalArgumentException(e);
        }

        final var module = modelContext.findModule(moduleName, rev).orElse(null);
        Preconditions.checkArgument(module != null,
                "Could not find module by name,revision: " + moduleName + "," + revision);

        final var schema = createSchemaFromUriInfo(uriInfo);
        final var host = createHostFromUriInfo(uriInfo);
        final var title = module.getName();
        final var url = schema + "://" + host + "/";
        final var basePath = getBasePath();
        final var modules = List.of(module);
        return new OpenApiInputStream(modelContext, title, url, SECURITY, deviceName, urlPrefix, true, false,
            modules, basePath, width, depth);
    }

    public String createHostFromUriInfo(final UriInfo uriInfo) {
        String portPart = "";
        final int port = uriInfo.getBaseUri().getPort();
        if (port != -1) {
            portPart = ":" + port;
        }
        return uriInfo.getBaseUri().getHost() + portPart;
    }

    public String createSchemaFromUriInfo(final UriInfo uriInfo) {
        return uriInfo.getBaseUri().getScheme();
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
        if (offset != 0 || limit != 0) {
            final var modules = modulesList(modulesWithoutDuplications);
            if (offset > modules.size() || offset < 0 || limit < 0) {
                return List.of();
            } else {
                final var end = limit == 0 ? modules.size() : Math.min(modules.size(), offset + limit);
                final var portionOfModules = modules.subList(offset, end);
                return portionOfModules;
            }
        }
        return modulesWithoutDuplications;
    }

    private static List<Module> modulesList(final List<Module> modulesWithoutDuplications) {
        return modulesWithoutDuplications
            .stream()
            .filter(BaseYangOpenApiGenerator::containsDataOrOperation)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private static boolean containsDataOrOperation(final Module module) {
        if (!module.getRpcs().isEmpty()) {
            return true;
        }
        return module.getChildNodes()
            .stream()
            .anyMatch(node -> node instanceof ContainerSchemaNode || node instanceof ListSchemaNode);
    }
}
