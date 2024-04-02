/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public OpenApiInputStream getControllerModulesDoc(final UriInfo uriInfo, final Integer offset, final Integer limit)
            throws IOException {
        final var context = requireNonNull(schemaService.getGlobalContext());
        final var schema = createSchemaFromUriInfo(uriInfo);
        final var host = createHostFromUriInfo(uriInfo);
        final var title = "Controller modules of RESTCONF";
        final var url = schema + "://" + host + "/";
        final var basePath = getBasePath();
        final var modules = getPortionOfModels(context, offset, limit);
        return new OpenApiInputStream(context, title, url, SECURITY, CONTROLLER_RESOURCE_NAME, "", false, false,
            modules, basePath);
    }

    public OpenApiInputStream getApiDeclaration(final String module, final String revision, final UriInfo uriInfo)
            throws IOException {
        final EffectiveModelContext schemaContext = schemaService.getGlobalContext();
        Preconditions.checkState(schemaContext != null);
        return getApiDeclaration(module, revision, uriInfo, schemaContext, "", CONTROLLER_RESOURCE_NAME);
    }

    public OpenApiInputStream getApiDeclaration(final String moduleName, final String revision, final UriInfo uriInfo,
            final EffectiveModelContext schemaContext, final String urlPrefix, final @NonNull String deviceName)
            throws IOException {
        final Optional<Revision> rev;

        try {
            rev = Revision.ofNullable(revision);
        } catch (final DateTimeParseException e) {
            throw new IllegalArgumentException(e);
        }

        final var module = schemaContext.findModule(moduleName, rev).orElse(null);
        Preconditions.checkArgument(module != null,
            "Could not find module by name,revision: " + moduleName + "," + revision);

        final var schema = createSchemaFromUriInfo(uriInfo);
        final var host = createHostFromUriInfo(uriInfo);
        final var title = module.getName();
        final var url = schema + "://" + host + "/";
        final var basePath = getBasePath();
        final var modules = List.of(module);
        return new OpenApiInputStream(schemaContext, title, url, SECURITY, deviceName, urlPrefix, true, false,
            modules, basePath);
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

    public boolean isForAllModules(final Integer offset, final Integer limit) {
        if (offset == null && limit == null) {
            return true;
        } else if (offset == null || limit == null) {
            return false;
        }
        return offset == 0 && limit == 0;
    }

    public Collection<? extends Module> getPortionOfModels(final EffectiveModelContext context, final Integer offset,
            final Integer limit) {
        if (!isForAllModules(offset, limit)) {
            final var augmentingModules = new ArrayList<Module>();
            final var modules = modulesList(context, augmentingModules);
            final var start = offset == null || offset < 0 ? 0 : offset;
            final var end = limit == null || limit <= 0 ? modules.size() : Math.min(modules.size(), start + limit);
            final var portionOfModules = start > modules.size() ? new ArrayList<Module>() : modules.subList(start, end);
            portionOfModules.addAll(augmentingModules);
            return portionOfModules;
        }
        return context.getModules();
    }

    private static List<Module> modulesList(final EffectiveModelContext context, final List<Module> augmentingModules) {
        final var modulesWithListOrContainer = new ArrayList<Module>();
        context.getModules().stream().forEach(module -> {
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
}
