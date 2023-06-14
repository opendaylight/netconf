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
import static org.opendaylight.restconf.openapi.impl.BaseYangOpenApiGenerator.API_VERSION;
import static org.opendaylight.restconf.openapi.impl.BaseYangOpenApiGenerator.BASE_PATH;
import static org.opendaylight.restconf.openapi.impl.BaseYangOpenApiGenerator.OPEN_API_BASIC_AUTH;
import static org.opendaylight.restconf.openapi.impl.BaseYangOpenApiGenerator.OPEN_API_VERSION;
import static org.opendaylight.restconf.openapi.impl.BaseYangOpenApiGenerator.SECURITY;
import static org.opendaylight.restconf.openapi.impl.BaseYangOpenApiGenerator.filterByRange;
import static org.opendaylight.restconf.openapi.impl.BaseYangOpenApiGenerator.getSortedModules;
import static org.opendaylight.restconf.openapi.impl.OpenApiServiceImpl.DEFAULT_PAGESIZE;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.DESCRIPTION_KEY;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.SUMMARY_SEPARATOR;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.buildTagsValue;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Range;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.impl.BaseYangOpenApiGenerator;
import org.opendaylight.restconf.openapi.impl.DefinitionNames;
import org.opendaylight.restconf.openapi.model.Components;
import org.opendaylight.restconf.openapi.model.Info;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.restconf.openapi.model.Operation;
import org.opendaylight.restconf.openapi.model.Path;
import org.opendaylight.restconf.openapi.model.Schema;
import org.opendaylight.restconf.openapi.model.SecuritySchemes;
import org.opendaylight.restconf.openapi.model.Server;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
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
            new TreeMap<>((o1, o2) -> o1.toString().compareToIgnoreCase(o2.toString()));
    private final Map<Long, YangInstanceIdentifier> longIdToInstanceId = new HashMap<>();

    private final Object lock = new Object();

    private final AtomicLong idKey = new AtomicLong(0);

    private ListenerRegistration<DOMMountPointListener> registration;

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
        synchronized (lock) {
            final SchemaContext context = globalSchema.getGlobalContext();
            for (final Entry<YangInstanceIdentifier, Long> entry : instanceIdToLongId.entrySet()) {
                final String modName = findModuleName(entry.getKey(), context);
                urlToId.put(openApiGenerator.generateUrlPrefixFromInstanceID(entry.getKey(), modName),
                        entry.getValue());
            }
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
        return openApiGenerator.generateUrlPrefixFromInstanceID(key, modName) + "yang-ext:mount";
    }

    private YangInstanceIdentifier getInstanceId(final Long id) {
        final YangInstanceIdentifier instanceId;
        synchronized (lock) {
            instanceId = longIdToInstanceId.get(id);
        }
        return instanceId;
    }

    private EffectiveModelContext getSchemaContext(final YangInstanceIdentifier id) {
        if (id == null) {
            return null;
        }

        checkState(mountService != null);
        return mountService.getMountPoint(id)
            .flatMap(mountPoint -> mountPoint.getService(DOMSchemaService.class))
            .flatMap(svc -> Optional.ofNullable(svc.getGlobalContext()))
            .orElse(null);
    }

    public OpenApiObject getMountPointApi(final UriInfo uriInfo, final Long id, final String module,
            final String revision) {
        final YangInstanceIdentifier iid = getInstanceId(id);
        final EffectiveModelContext context = getSchemaContext(iid);
        final String urlPrefix = getYangMountUrl(iid);
        final String deviceName  = extractDeviceName(iid);

        if (context == null) {
            return null;
        }

        if (DATASTORES_LABEL.equals(module) && DATASTORES_REVISION.equals(revision)) {
            return generateDataStoreOpenApi(uriInfo, urlPrefix, deviceName);
        }
        return openApiGenerator.getApiDeclaration(module, revision, uriInfo, context, urlPrefix);
    }

    public OpenApiObject getMountPointApi(final UriInfo uriInfo, final Long id, final @Nullable String strPageNum) {
        final var iid = getInstanceId(id);
        final var context = getSchemaContext(iid);
        final var urlPrefix = getYangMountUrl(iid);
        final var deviceName = extractDeviceName(iid);

        if (context == null) {
            return null;
        }
        final var definitionNames = new DefinitionNames();

        boolean includeDataStore = true;
        Range<Integer> range = Range.all();
        if (strPageNum != null) {
            final var pageNum = Integer.parseInt(strPageNum);
            final var end = DEFAULT_PAGESIZE * pageNum - 1;
            int start = end - DEFAULT_PAGESIZE;
            if (pageNum == 1) {
                start++;
            } else {
                includeDataStore = false;
            }
            range = Range.closed(start, end);
        }

        final var schema = openApiGenerator.createSchemaFromUriInfo(uriInfo);
        final var host = openApiGenerator.createHostFromUriInfo(uriInfo);
        final var title = deviceName + " modules of RESTCONF";
        final var info = new Info(API_VERSION, title);
        final var servers = List.of(new Server(schema + "://" + host + BASE_PATH));

        final var modules = getSortedModules(context);
        final var filteredModules = filterByRange(modules, range);
        final var paths = new HashMap<String, Path>();
        final var schemas = new HashMap<String, Schema>();
        for (final var module : filteredModules) {
            LOG.debug("Working on [{},{}]...", module.getName(), module.getQNameModule().getRevision().orElse(null));
            schemas.putAll(openApiGenerator.getSchemas(module, context, definitionNames, false));
            paths.putAll(openApiGenerator.getPaths(module, urlPrefix, deviceName, context, definitionNames, false));
        }
        final var components = new Components(schemas, new SecuritySchemes(OPEN_API_BASIC_AUTH));
        if (includeDataStore) {
            paths.putAll(getDataStoreApiPaths(urlPrefix, deviceName));
        }
        return new OpenApiObject(OPEN_API_VERSION, info, servers, paths, components, SECURITY);
    }

    private static String extractDeviceName(final YangInstanceIdentifier iid) {
        return ((YangInstanceIdentifier.NodeIdentifierWithPredicates.Singleton)iid.getLastPathArgument())
                .values().getElement().toString();
    }

    private OpenApiObject generateDataStoreOpenApi(final UriInfo uriInfo, final String context,
            final String deviceName) {
        final var info = new Info(API_VERSION, context);
        final var schema = openApiGenerator.createSchemaFromUriInfo(uriInfo);
        final var host = openApiGenerator.createHostFromUriInfo(uriInfo);
        final var servers = List.of(new Server(schema + "://" + host + BASE_PATH));
        final var components = new Components(new HashMap<>(), new SecuritySchemes(OPEN_API_BASIC_AUTH));
        final var paths = getDataStoreApiPaths(context, deviceName);
        return new OpenApiObject(OPEN_API_VERSION, info, servers, paths, components, SECURITY);
    }

    private Map<String, Path> getDataStoreApiPaths(final String context, final String deviceName) {
        final var dataBuilder = new Path.Builder();
        dataBuilder.get(createGetPathItem("data",
                "Queries the config (startup) datastore on the mounted hosted.", deviceName));

        final var operationsBuilder = new Path.Builder();
        operationsBuilder.get(createGetPathItem("operations",
                "Queries the available operations (RPC calls) on the mounted hosted.", deviceName));

        return Map.of(openApiGenerator.getResourcePath("data", context), dataBuilder.build(),
            openApiGenerator.getResourcePath("operations", context), operationsBuilder.build());
    }

    private static Operation createGetPathItem(final String resourceType, final String description,
            final String deviceName) {
        final String summary = HttpMethod.GET + SUMMARY_SEPARATOR + deviceName + SUMMARY_SEPARATOR + resourceType;
        final ArrayNode tags = buildTagsValue(deviceName, "GET root");
        final ObjectNode okResponse = JsonNodeFactory.instance.objectNode();
        okResponse.put(DESCRIPTION_KEY, Response.Status.OK.getReasonPhrase());
        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.OK.getStatusCode()), okResponse);
        return new Operation.Builder()
            .tags(tags)
            .responses(responses)
            .description(description)
            .summary(summary)
            .build();
    }

    @Override
    public void onMountPointCreated(final YangInstanceIdentifier path) {
        synchronized (lock) {
            LOG.debug("Mount point {} created", path);
            final Long idLong = idKey.incrementAndGet();
            instanceIdToLongId.put(path, idLong);
            longIdToInstanceId.put(idLong, path);
        }
    }

    @Override
    public void onMountPointRemoved(final YangInstanceIdentifier path) {
        synchronized (lock) {
            LOG.debug("Mount point {} removed", path);
            final Long id = instanceIdToLongId.remove(path);
            longIdToInstanceId.remove(id);
        }
    }
}
