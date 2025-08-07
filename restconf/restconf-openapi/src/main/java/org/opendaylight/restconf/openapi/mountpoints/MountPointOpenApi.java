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
import static org.opendaylight.restconf.openapi.impl.BaseYangOpenApiGenerator.SECURITY;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.impl.BaseYangOpenApiGenerator;
import org.opendaylight.restconf.openapi.model.DocumentEntity;
import org.opendaylight.restconf.openapi.model.MetadataEntity;
import org.opendaylight.restconf.openapi.model.MountPointsEntity;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
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

    public MountPointsEntity getInstanceIdentifiers() {
        final Map<String, Long> urlToId = new HashMap<>();
        final EffectiveModelContext modelContext = globalSchema.getGlobalContext();
        for (final Entry<YangInstanceIdentifier, Long> entry : instanceIdToLongId.entrySet()) {
            final String modName = findModuleName(entry.getKey(), modelContext);
            urlToId.put(generateUrlPrefixFromInstanceID(entry.getKey(), modName), entry.getValue());
        }
        return new MountPointsEntity(urlToId);
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
        for (var arg : key.getPathArguments()) {
            final String name = arg.getNodeType().getLocalName();
            if (arg instanceof NodeIdentifierWithPredicates nodeId) {
                for (var entry : nodeId.entrySet()) {
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

    public DocumentEntity getMountPointApi(final URI uri, final long id, final String module, final String revision,
            final int width, final int depth) throws IOException  {
        final YangInstanceIdentifier iid = longIdToInstanceId.get(id);
        final EffectiveModelContext modelContext = getModelContext(iid);
        final String urlPrefix = getYangMountUrl(iid);
        final String deviceName = extractDeviceName(iid);

        if (modelContext == null) {
            return null;
        }

        if (DATASTORES_LABEL.equals(module) && DATASTORES_REVISION.equals(revision)) {
            return generateDataStoreOpenApi(modelContext, uri, urlPrefix, deviceName, width, depth);
        }
        return openApiGenerator.getApiDeclaration(module, revision, uri, modelContext, urlPrefix, deviceName,
            width, depth);
    }

    public DocumentEntity getMountPointApi(final URI uri, final long id, final int width, final int depth,
            final int offset, final int limit) throws IOException {
        final var iid = longIdToInstanceId.get(id);
        final var context = getModelContext(iid);
        final var urlPrefix = getYangMountUrl(iid);
        final var deviceName = extractDeviceName(iid);

        if (context == null) {
            return null;
        }

        final boolean includeDataStore = limit == 0 && offset == 0;
        final var modulesWithoutDuplications = BaseYangOpenApiGenerator.getModulesWithoutDuplications(context);
        final var portionOfModules = BaseYangOpenApiGenerator.getModelsSublist(modulesWithoutDuplications,
            offset, limit);
        final var schema = openApiGenerator.createSchemaFromUri(uri);
        final var host = openApiGenerator.createHostFromUri(uri);
        final var title = deviceName + " modules of RESTCONF";
        final var url = schema + "://" + host + "/";
        final var basePath = openApiGenerator.getBasePath();
        return new DocumentEntity(context, title, url, SECURITY, deviceName, urlPrefix, false, includeDataStore,
            portionOfModules, basePath, width, depth);
    }

    public MetadataEntity getMountPointApiMeta(final long id, final int offset, final int limit) throws IOException {
        final var iid = longIdToInstanceId.get(id);
        final var context = getModelContext(iid);

        if (context == null) {
            return null;
        }

        final var modulesWithoutDuplications = BaseYangOpenApiGenerator.getModulesWithoutDuplications(context);
        return new MetadataEntity(offset, limit, modulesWithoutDuplications.size(),
            BaseYangOpenApiGenerator.configModulesList(modulesWithoutDuplications).size());
    }

    private static String extractDeviceName(final YangInstanceIdentifier iid) {
        return ((NodeIdentifierWithPredicates.Singleton) iid.getLastPathArgument()).values().getFirst().toString();
    }

    private DocumentEntity generateDataStoreOpenApi(final EffectiveModelContext modelContext, final URI uri,
            final String urlPrefix, final String deviceName, final int width, final int depth) throws IOException {
        final var schema = openApiGenerator.createSchemaFromUri(uri);
        final var host = openApiGenerator.createHostFromUri(uri);
        final var url = schema + "://" + host + "/";
        final var basePath = openApiGenerator.getBasePath();
        final var modules = BaseYangOpenApiGenerator.getModulesWithoutDuplications(modelContext);
        return new DocumentEntity(modelContext, urlPrefix, url, SECURITY, deviceName, urlPrefix, true, false,
            modules, basePath, width, depth);
    }

    @Override
    public void onMountPointCreated(final DOMMountPoint mountPoint) {
        final var path = mountPoint.getIdentifier();
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
