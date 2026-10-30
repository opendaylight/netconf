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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.model.DocumentEntity;
import org.opendaylight.restconf.openapi.model.MetadataEntity;
import org.opendaylight.restconf.openapi.model.security.OpenApiOauth2Configuration;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseYangOpenApiGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(BaseYangOpenApiGenerator.class);
    private static final String CONTROLLER_RESOURCE_NAME = "Controller";
    private static final Pattern FORWARDED_PROTO_PATTERN = Pattern.compile("(?i)proto=\"?(\\w+)\"?");

    private final DOMSchemaService schemaService;
    private final @Nullable OpenApiOauth2Configuration oauth2Config;
    private final @Nullable String configuredScheme;

    protected BaseYangOpenApiGenerator(final @NonNull DOMSchemaService schemaService) {
        this(schemaService, null);
    }

    protected BaseYangOpenApiGenerator(final @NonNull DOMSchemaService schemaService,
            final @Nullable OpenApiOauth2Configuration oauth2Config) {
        this(schemaService, oauth2Config, null);
    }

    /**
     * Creates generator.
     *
     * @param schemaService schema service
     * @param oauth2Config OAuth2 configuration, {@code null} when OAuth2 is disabled
     * @param scheme scheme used in generated documents ({@code http} or {@code https}), {@code null} or blank to
     *     resolve it from proxy headers and request URI
     */
    protected BaseYangOpenApiGenerator(final @NonNull DOMSchemaService schemaService,
            final @Nullable OpenApiOauth2Configuration oauth2Config, final @Nullable String scheme) {
        this.schemaService = requireNonNull(schemaService);
        this.oauth2Config = oauth2Config;
        configuredScheme = normalizeScheme(scheme);
    }

    public @Nullable OpenApiOauth2Configuration oauth2Config() {
        return oauth2Config;
    }

    /**
     * Returns the security requirements list for use in the OpenAPI document {@code security} section.
     * Each entry in the outer list is an alternative (OR); all entries within a single map are conjunctions (AND).
     * When OAuth2 is not configured only HTTP Basic is advertised.
     */
    public List<Map<String, List<String>>> securityRequirements() {
        if (oauth2Config == null) {
            return List.of(Map.of("basicAuth", List.of()));
        }
        return List.of(Map.of("basicAuth", List.of()), Map.of("oauth2", List.of()));
    }

    private @NonNull EffectiveModelContext modelContext() {
        return schemaService.getGlobalContext();
    }

    public DocumentEntity getControllerModulesDoc(final URI uri, final int width, final int depth,
            final int offset, final int limit, final String basePath, final @Nullable String forwardedHeader,
            final @Nullable String forwardedProtoHeader) {
        final var modelContext = modelContext();
        final var scheme = resolveScheme(uri, forwardedHeader, forwardedProtoHeader);
        final var host = createHostFromUri(uri);
        final var title = "Controller modules of RESTCONF";
        final var url = scheme + "://" + host + "/";
        final var modulesWithoutDuplications = getModulesWithoutDuplications(modelContext);
        final var portionOfModels = getModelsSublist(modulesWithoutDuplications, offset, limit);
        return new DocumentEntity(modelContext, title, url, securityRequirements(), CONTROLLER_RESOURCE_NAME, "",
            false, true, portionOfModels, basePath, width, depth, oauth2Config);
    }

    public MetadataEntity getControllerModulesMeta(final int offset, final int limit) throws IOException {
        final var modulesWithoutDuplications = getModulesWithoutDuplications(modelContext());
        return new MetadataEntity(offset, limit, modulesWithoutDuplications.size(),
            configModulesList(modulesWithoutDuplications).size());
    }

    public DocumentEntity getApiDeclaration(final String module, final String revision, final URI uri, final int width,
            final int depth, final String basePath, final @Nullable String forwardedHeader,
            final @Nullable String forwardedProtoHeader) throws IOException {
        return getApiDeclaration(module, revision, uri, modelContext(), "", CONTROLLER_RESOURCE_NAME, width, depth,
            basePath, forwardedHeader, forwardedProtoHeader);
    }

    public DocumentEntity getApiDeclaration(final String moduleName, final String revision, final URI uri,
            final EffectiveModelContext modelContext, final String urlPrefix, final @NonNull String deviceName,
            final int width, final int depth, final String basePath, final @Nullable String forwardedHeader,
            final @Nullable String forwardedProtoHeader) throws IOException {
        final Optional<Revision> rev;

        try {
            rev = Revision.ofNullable(revision);
        } catch (final DateTimeParseException e) {
            throw new IOException("Failed to parse revision", e);
        }

        final var module = modelContext.findModule(moduleName, rev).orElseThrow(
            () -> new IOException("Could not find module by name,revision: " + moduleName + "," + revision));

        final var scheme = resolveScheme(uri, forwardedHeader, forwardedProtoHeader);
        final var host = createHostFromUri(uri);
        final var title = module.getName();
        final var url = scheme + "://" + host + "/";
        final var modules = List.of(module);
        return new DocumentEntity(modelContext, title, url, securityRequirements(), deviceName, urlPrefix, true,
            false, modules, basePath, width, depth, oauth2Config);
    }

    public String createHostFromUri(final URI uri) {
        String portPart = "";
        final int port = uri.getPort();
        if (port != -1) {
            portPart = ":" + port;
        }
        return uri.getHost() + portPart;
    }

    public String resolveScheme(final URI uri, final @Nullable String forwardedHeader,
            final @Nullable String forwardedProtoHeader) {
        // Configured scheme
        if (configuredScheme != null) {
            return configuredScheme;
        }

        // RFC 7239 Forwarded header
        if (forwardedHeader != null) {
            final var firstElement = forwardedHeader.split(",", 2)[0];
            final var matcher = FORWARDED_PROTO_PATTERN.matcher(firstElement);
            if (matcher.find()) {
                final var proto = matcher.group(1).toLowerCase(Locale.ROOT);
                if (isSupportedScheme(proto)) {
                    return proto;
                }
            }
        }

        // X-Forwarded-Proto header
        if (forwardedProtoHeader != null) {
            final var firstProto = forwardedProtoHeader.split(",", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (isSupportedScheme(firstProto)) {
                return firstProto;
            }
        }

        // Scheme from URI
        return uri.getScheme();
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

    private static @Nullable String normalizeScheme(final @Nullable String scheme) {
        if (scheme == null || scheme.isBlank()) {
            return null;
        }
        final var normalized = scheme.trim().toLowerCase(Locale.ROOT);
        if (isSupportedScheme(normalized)) {
            return normalized;
        }
        LOG.warn("Ignoring unsupported OpenAPI scheme {}, only http and https are supported", scheme);
        return null;
    }

    private static boolean isSupportedScheme(final String scheme) {
        return "http".equals(scheme) || "https".equals(scheme);
    }
}
