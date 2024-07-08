/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.mountpoints;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static org.opendaylight.restconf.openapi.impl.BaseYangOpenApiGenerator.SECURITY;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.impl.BaseYangOpenApiGenerator;
import org.opendaylight.restconf.openapi.impl.OpenApiInputStream;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MountPointOpenApi implements DOMMountPointListener, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MountPointOpenApi.class);
    private static final String DATASTORES_REVISION = "-";
    private static final String DATASTORES_LABEL = "Datastores";

    private final DOMSchemaService globalSchema;
    private final DOMMountPointService mountService;
    private final BaseYangOpenApiGenerator openApiGenerator;
    private final Map<YangInstanceIdentifier, Long> instanceIdToLongId =
            new ConcurrentSkipListMap<>((o1, o2) -> o1.toString().compareToIgnoreCase(o2.toString()));
    private final Map<Long, YangInstanceIdentifier> longIdToInstanceId = new ConcurrentHashMap<>();
    private final AtomicLong idKey = new AtomicLong(0);

    private Registration registration;

    public MountPointOpenApi(final DOMSchemaService globalSchema, final DOMMountPointService mountService,
            final BaseYangOpenApiGenerator openApiGenerator) {
        this.globalSchema = requireNonNull(globalSchema);
        this.mountService = requireNonNull(mountService);
        this.openApiGenerator = requireNonNull(openApiGenerator);
    }

    public void init() {
        registration = mountService.registerProvisionListener(this);
    }

    @Override
    public void close() {
        if (registration != null) {
            registration.close();
        }
    }

    public Map<String, Long> getInstanceIdentifiers() {
        final Map<String, Long> urlToId = new HashMap<>();
        final EffectiveModelContext modelContext = globalSchema.getGlobalContext();
        for (final Entry<YangInstanceIdentifier, Long> entry : instanceIdToLongId.entrySet()) {
            final String modName = findModuleName(entry.getKey(), modelContext);
            urlToId.put(generateUrlPrefixFromInstanceID(entry.getKey(), modName), entry.getValue());
        }
        return urlToId;
    }

    private static String findModuleName(final YangInstanceIdentifier id, final EffectiveModelContext modelContext) {
        final PathArgument rootQName = id.getPathArguments().iterator().next();
        for (final Module mod : modelContext.getModules()) {
            if (mod.findDataChildByName(rootQName.getNodeType()).isPresent()) {
                return mod.getName();
            }
        }
        return null;
    }

    private String getYangMountUrl(final YangInstanceIdentifier key) {
        final String modName = findModuleName(key, globalSchema.getGlobalContext());
        return generateUrlPrefixFromInstanceID(key, modName) + "yang-ext:mount";
    }

    private static String generateUrlPrefixFromInstanceID(final YangInstanceIdentifier key, final String moduleName) {
        final StringBuilder builder = new StringBuilder();
        builder.append("/");
        if (moduleName != null) {
            builder.append(moduleName).append(':');
        }
        for (final PathArgument arg : key.getPathArguments()) {
            final String name = arg.getNodeType().getLocalName();
            if (arg instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates nodeId) {
                for (final Entry<QName, Object> entry : nodeId.entrySet()) {
                    builder.deleteCharAt(builder.length() - 1).append("=").append(entry.getValue()).append('/');
                }
            } else {
                builder.append(name).append('/');
            }
        }
        return builder.toString();
    }

    private EffectiveModelContext getModelContext(final YangInstanceIdentifier id) {
        if (id == null) {
            return null;
        }

        checkState(mountService != null);
        return mountService.getMountPoint(id)
            .flatMap(mountPoint -> mountPoint.getService(DOMSchemaService.class))
            .map(DOMSchemaService::getGlobalContext)
            .orElse(null);
    }

    public OpenApiInputStream getMountPointApi(final UriInfo uriInfo, final Long id, final String module,
            final String revision, final Integer width, final Integer depth) throws IOException  {
        final YangInstanceIdentifier iid = longIdToInstanceId.get(id);
        final EffectiveModelContext modelContext = getModelContext(iid);
        final String urlPrefix = getYangMountUrl(iid);
        final String deviceName = extractDeviceName(iid);

        if (modelContext == null) {
            return null;
        }

        if (DATASTORES_LABEL.equals(module) && DATASTORES_REVISION.equals(revision)) {
            return generateDataStoreOpenApi(modelContext, uriInfo, urlPrefix, deviceName, width, depth);
        }
        return openApiGenerator.getApiDeclaration(module, revision, uriInfo, modelContext, urlPrefix, deviceName,
            width, depth);
    }

    public OpenApiInputStream getMountPointApi(final UriInfo uriInfo, final Long id, final Integer width,
            final Integer depth, final Integer offset, final Integer limit) throws IOException {
        final var iid = longIdToInstanceId.get(id);
        final var context = getModelContext(iid);
        final var urlPrefix = getYangMountUrl(iid);
        final var deviceName = extractDeviceName(iid);

        if (context == null) {
            return null;
        }

        final var nonNullOffset = requireNonNullElse(offset, 0);
        final var nonNullLimit = requireNonNullElse(limit, 0);

        final boolean includeDataStore = nonNullLimit == 0 && nonNullOffset == 0;
        final var modulesWithoutDuplications = BaseYangOpenApiGenerator.getModulesWithoutDuplications(context);
        final var portionOfModules = BaseYangOpenApiGenerator.getModelsSublist(modulesWithoutDuplications,
            nonNullOffset, nonNullLimit);
        final var schema = openApiGenerator.createSchemaFromUriInfo(uriInfo);
        final var host = openApiGenerator.createHostFromUriInfo(uriInfo);
        final var title = deviceName + " modules of RESTCONF";
        final var url = schema + "://" + host + "/";
        final var basePath = openApiGenerator.getBasePath();
        return new OpenApiInputStream(context, title, url, SECURITY, deviceName, urlPrefix, false, includeDataStore,
            portionOfModules, basePath, width, depth);
    }

    private static String extractDeviceName(final YangInstanceIdentifier iid) {
        return ((YangInstanceIdentifier.NodeIdentifierWithPredicates.Singleton)iid.getLastPathArgument())
                .values().getElement().toString();
    }

    private OpenApiInputStream generateDataStoreOpenApi(final EffectiveModelContext modelContext,
            final UriInfo uriInfo, final String urlPrefix, final String deviceName, final Integer width,
            final Integer depth) throws IOException {
        final var schema = openApiGenerator.createSchemaFromUriInfo(uriInfo);
        final var host = openApiGenerator.createHostFromUriInfo(uriInfo);
        final var url = schema + "://" + host + "/";
        final var basePath = openApiGenerator.getBasePath();
        final var modules = BaseYangOpenApiGenerator.getModulesWithoutDuplications(modelContext);
        return new OpenApiInputStream(modelContext, urlPrefix, url, SECURITY, deviceName, urlPrefix, true, false,
            modules, basePath, width, depth);
    }

    @Override
    public void onMountPointCreated(final YangInstanceIdentifier path) {
        LOG.debug("Mount point {} created", path);
        final Long idLong = idKey.incrementAndGet();
        instanceIdToLongId.put(path, idLong);
        longIdToInstanceId.put(idLong, path);
    }

    @Override
    public void onMountPointRemoved(final YangInstanceIdentifier path) {
        LOG.debug("Mount point {} removed", path);
        final Long id = instanceIdToLongId.remove(path);
        longIdToInstanceId.remove(id);
    }
}
