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
import static org.opendaylight.restconf.openapi.impl.BaseYangOpenApiGenerator.BASE_PATH;
import static org.opendaylight.restconf.openapi.impl.BaseYangOpenApiGenerator.SECURITY;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.Nullable;
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
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
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

    private final Object lock = new Object();

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
        final SchemaContext context = globalSchema.getGlobalContext();
        for (final Entry<YangInstanceIdentifier, Long> entry : instanceIdToLongId.entrySet()) {
            final String modName = findModuleName(entry.getKey(), context);
            urlToId.put(generateUrlPrefixFromInstanceID(entry.getKey(), modName), entry.getValue());
        }
        return urlToId;
    }

    private static String findModuleName(final YangInstanceIdentifier id, final SchemaContext context) {
        final PathArgument rootQName = id.getPathArguments().iterator().next();
        for (final Module mod : context.getModules()) {
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

    private EffectiveModelContext getSchemaContext(final YangInstanceIdentifier id) {
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
            final String revision) throws IOException  {
        final YangInstanceIdentifier iid = longIdToInstanceId.get(id);
        final EffectiveModelContext context = getSchemaContext(iid);
        final String urlPrefix = getYangMountUrl(iid);
        final String deviceName = extractDeviceName(iid);

        if (context == null) {
            return null;
        }

        if (DATASTORES_LABEL.equals(module) && DATASTORES_REVISION.equals(revision)) {
            return generateDataStoreOpenApi(context, uriInfo, urlPrefix, deviceName);
        }
        return openApiGenerator.getApiDeclaration(module, revision, uriInfo, context, urlPrefix, deviceName);
    }

    public OpenApiInputStream getMountPointApi(final UriInfo uriInfo, final Long id, final @Nullable String strPageNum)
            throws IOException {
        final var iid = longIdToInstanceId.get(id);
        final var context = getSchemaContext(iid);
        final var urlPrefix = getYangMountUrl(iid);
        final var deviceName = extractDeviceName(iid);

        if (context == null) {
            return null;
        }

        boolean includeDataStore = true;
        if (strPageNum != null) {
            final var pageNum = Integer.parseInt(strPageNum);
            if (pageNum != 1) {
                includeDataStore = false;
            }
        }

        final var schema = openApiGenerator.createSchemaFromUriInfo(uriInfo);
        final var host = openApiGenerator.createHostFromUriInfo(uriInfo);
        final var title = deviceName + " modules of RESTCONF";
        final var url = schema + "://" + host + BASE_PATH;
        final var modules = context.getModules();
        return new OpenApiInputStream(context, title, url, SECURITY, deviceName, urlPrefix, false, includeDataStore,
            modules);
    }

    private static String extractDeviceName(final YangInstanceIdentifier iid) {
        return ((YangInstanceIdentifier.NodeIdentifierWithPredicates.Singleton)iid.getLastPathArgument())
                .values().getElement().toString();
    }

    private OpenApiInputStream generateDataStoreOpenApi(final EffectiveModelContext modelContext,
            final UriInfo uriInfo, final String urlPrefix, final String deviceName) throws IOException {
        final var schema = openApiGenerator.createSchemaFromUriInfo(uriInfo);
        final var host = openApiGenerator.createHostFromUriInfo(uriInfo);
        final var url = schema + "://" + host + BASE_PATH;
        final var modules = modelContext.getModules();
        return new OpenApiInputStream(modelContext, urlPrefix, url, SECURITY, deviceName, urlPrefix, true, false,
            modules);
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
