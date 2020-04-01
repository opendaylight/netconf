/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.mountpoints;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.sal.rest.doc.impl.BaseYangSwaggerGenerator.BASE_PATH;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.DESCRIPTION_KEY;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.RESPONSES_KEY;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.SUMMARY_KEY;
import static org.opendaylight.netconf.sal.rest.doc.util.JsonUtil.addFields;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.URIType;
import org.opendaylight.netconf.sal.rest.doc.impl.BaseYangSwaggerGenerator;
import org.opendaylight.netconf.sal.rest.doc.impl.DefinitionNames;
import org.opendaylight.netconf.sal.rest.doc.swagger.Resource;
import org.opendaylight.netconf.sal.rest.doc.swagger.ResourceList;
import org.opendaylight.netconf.sal.rest.doc.swagger.SwaggerObject;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MountPointSwagger implements DOMMountPointListener, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MountPointSwagger.class);

    private static final String DATASTORES_REVISION = "-";
    private static final String DATASTORES_LABEL = "Datastores";

    private final DOMSchemaService globalSchema;
    private final DOMMountPointService mountService;
    private final BaseYangSwaggerGenerator swaggerGenerator;
    private final Map<YangInstanceIdentifier, Long> instanceIdToLongId =
            new TreeMap<>((o1, o2) -> o1.toString().compareToIgnoreCase(o2.toString()));
    private final Map<Long, YangInstanceIdentifier> longIdToInstanceId = new HashMap<>();

    private final Object lock = new Object();

    private final AtomicLong idKey = new AtomicLong(0);

    private ListenerRegistration<DOMMountPointListener> registration;

    public MountPointSwagger(final DOMSchemaService globalSchema, final DOMMountPointService mountService,
                             final BaseYangSwaggerGenerator swaggerGenerator) {
        this.globalSchema = requireNonNull(globalSchema);
        this.mountService = requireNonNull(mountService);
        this.swaggerGenerator = requireNonNull(swaggerGenerator);
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
        synchronized (this.lock) {
            final SchemaContext context = this.globalSchema.getGlobalContext();
            for (final Entry<YangInstanceIdentifier, Long> entry : this.instanceIdToLongId.entrySet()) {
                final String modName = findModuleName(entry.getKey(), context);
                urlToId.put(swaggerGenerator.generateUrlPrefixFromInstanceID(entry.getKey(), modName),
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
        final String modName = findModuleName(key, this.globalSchema.getGlobalContext());
        return swaggerGenerator.generateUrlPrefixFromInstanceID(key, modName) + "yang-ext:mount";
    }

    public ResourceList getResourceList(final UriInfo uriInfo, final Long id, final URIType uriType) {
        return getResourceList(uriInfo, id, 0, true, uriType);
    }

    public ResourceList getResourceList(final UriInfo uriInfo, final Long id, final int pageNum, final boolean all,
                                        final URIType uriType) {
        final YangInstanceIdentifier iid = getInstanceId(id);
        if (iid == null) {
            return null; // indicating not found.
        }
        final SchemaContext context = getSchemaContext(iid);
        if (context == null) {
            return swaggerGenerator.createResourceList();
        }
        final List<Resource> resources = new LinkedList<>();
        final Resource dataStores = new Resource();
        dataStores.setDescription("Provides methods for accessing the data stores.");
        dataStores.setPath(swaggerGenerator.generatePath(uriInfo, DATASTORES_LABEL, DATASTORES_REVISION));
        resources.add(dataStores);
        final String urlPrefix = getYangMountUrl(iid);
        final ResourceList list =
                swaggerGenerator.getResourceListing(uriInfo, context, urlPrefix, pageNum, all, uriType);
        resources.addAll(list.getApis());
        list.setApis(resources);
        return list;
    }

    private YangInstanceIdentifier getInstanceId(final Long id) {
        final YangInstanceIdentifier instanceId;
        synchronized (this.lock) {
            instanceId = this.longIdToInstanceId.get(id);
        }
        return instanceId;
    }

    private SchemaContext getSchemaContext(final YangInstanceIdentifier id) {
        if (id == null) {
            return null;
        }

        checkState(mountService != null);
        final Optional<DOMMountPoint> mountPoint = this.mountService.getMountPoint(id);
        if (mountPoint.isEmpty()) {
            return null;
        }

        final SchemaContext context = mountPoint.get().getSchemaContext();
        return context;
    }

    public SwaggerObject getMountPointApi(final UriInfo uriInfo, final Long id, final String module,
                                          final String revision, final URIType uriType) {
        final YangInstanceIdentifier iid = getInstanceId(id);
        final SchemaContext context = getSchemaContext(iid);
        final String urlPrefix = getYangMountUrl(iid);
        if (context == null) {
            return null;
        }

        if (DATASTORES_LABEL.equals(module) && DATASTORES_REVISION.equals(revision)) {
            return generateDataStoreApiDoc(uriInfo, urlPrefix);
        }
        return swaggerGenerator.getApiDeclaration(module, revision, uriInfo, context, urlPrefix, uriType);
    }

    public void appendDocWithMountModules(final UriInfo uriInfo, final SwaggerObject doc,
                                          final DefinitionNames definitionNames, final URIType uriType) {
        synchronized (this.lock) {
            for (final YangInstanceIdentifier iid : instanceIdToLongId.keySet()) {
                final SchemaContext schemaContext = getSchemaContext(iid);
                final String urlPrefix = getYangMountUrl(iid);
                final String deviceName = extractDeviceName(iid);

                swaggerGenerator.fillDoc(doc, schemaContext, urlPrefix, Optional.of(deviceName),
                        uriType, definitionNames);

                final SwaggerObject dataStoreDoc = generateDataStoreApiDoc(uriInfo, urlPrefix);

                addFields(doc.getPaths(), dataStoreDoc.getPaths().fields());
            }
        }
    }

    private static String extractDeviceName(final YangInstanceIdentifier iid) {
        return ((YangInstanceIdentifier.NodeIdentifierWithPredicates.Singleton)iid.getLastPathArgument())
                .values().getElement().toString();
    }

    private SwaggerObject generateDataStoreApiDoc(final UriInfo uriInfo, final String moduleName) {
        final SwaggerObject declaration = swaggerGenerator.createSwaggerObject(
                swaggerGenerator.createSchemaFromUriInfo(uriInfo),
                swaggerGenerator.createHostFromUriInfo(uriInfo),
                BASE_PATH,
                moduleName);

        final ObjectNode pathsObject = JsonNodeFactory.instance.objectNode();
        createGetPathItem("config", "Queries the config (startup) datastore on the mounted hosted.",
                moduleName, pathsObject);
        createGetPathItem("operational", "Queries the operational (running) datastore on the mounted hosted.",
                moduleName, pathsObject);
        createGetPathItem("operations", "Queries the available operations (RPC calls) on the mounted hosted.",
                moduleName, pathsObject);

        declaration.setPaths(pathsObject);
        declaration.setDefinitions(JsonNodeFactory.instance.objectNode());

        return declaration;
    }

    private void createGetPathItem(final String datastore, final String description, final String context,
                                   final ObjectNode pathsObject) {
        final ObjectNode pathItem = JsonNodeFactory.instance.objectNode();
        final ObjectNode operationObject = JsonNodeFactory.instance.objectNode();
        pathItem.set("get", operationObject);
        operationObject.put(DESCRIPTION_KEY, description);
        operationObject.put(SUMMARY_KEY, HttpMethod.GET + " " + swaggerGenerator.getResourcePathPart(datastore));
        final ObjectNode okResponse = JsonNodeFactory.instance.objectNode();
        okResponse.put(DESCRIPTION_KEY, Response.Status.OK.getReasonPhrase());
        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.OK.getStatusCode()), okResponse);
        operationObject.set(RESPONSES_KEY, responses);
        pathsObject.set(swaggerGenerator.getResourcePath(datastore, context), pathItem);
    }

    @Override
    public void onMountPointCreated(final YangInstanceIdentifier path) {
        synchronized (this.lock) {
            final Long idLong = this.idKey.incrementAndGet();
            this.instanceIdToLongId.put(path, idLong);
            this.longIdToInstanceId.put(idLong, path);
        }
    }

    @Override
    public void onMountPointRemoved(final YangInstanceIdentifier path) {
        synchronized (this.lock) {
            final Long id = this.instanceIdToLongId.remove(path);
            this.longIdToInstanceId.remove(id);
        }
    }
}
