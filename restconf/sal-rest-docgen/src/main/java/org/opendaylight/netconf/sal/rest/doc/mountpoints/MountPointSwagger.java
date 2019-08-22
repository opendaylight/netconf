/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.mountpoints;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.doc.impl.BaseYangSwaggerGenerator;
import org.opendaylight.netconf.sal.rest.doc.swagger.Api;
import org.opendaylight.netconf.sal.rest.doc.swagger.ApiDeclaration;
import org.opendaylight.netconf.sal.rest.doc.swagger.Operation;
import org.opendaylight.netconf.sal.rest.doc.swagger.Resource;
import org.opendaylight.netconf.sal.rest.doc.swagger.ResourceList;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class MountPointSwagger implements DOMMountPointListener, AutoCloseable {

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

    public ResourceList getResourceList(final UriInfo uriInfo, final Long id) {
        return getResourceList(uriInfo, id, 0, true);
    }

    public ResourceList getResourceList(final UriInfo uriInfo, final Long id, final int pageNum, boolean all) {
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
        final ResourceList list = swaggerGenerator.getResourceListing(uriInfo, context, urlPrefix, pageNum, all);
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
        if (!mountPoint.isPresent()) {
            return null;
        }

        final SchemaContext context = mountPoint.get().getSchemaContext();
        if (context == null) {
            return null;
        }
        return context;
    }

    public ApiDeclaration getMountPointApi(final UriInfo uriInfo, final Long id, final String module,
            final String revision) {
        final YangInstanceIdentifier iid = getInstanceId(id);
        final SchemaContext context = getSchemaContext(iid);
        final String urlPrefix = getYangMountUrl(iid);
        if (context == null) {
            return null;
        }

        if (DATASTORES_LABEL.equals(module) && DATASTORES_REVISION.equals(revision)) {
            return generateDataStoreApiDoc(uriInfo, urlPrefix);
        }
        return swaggerGenerator.getApiDeclaration(module, revision, uriInfo, context, urlPrefix);
    }

    private ApiDeclaration generateDataStoreApiDoc(final UriInfo uriInfo, final String context) {
        final List<Api> apis = new LinkedList<>();
        apis.add(createGetApi("config", "Queries the config (startup) datastore on the mounted hosted.", context));
        apis.add(createGetApi("operational", "Queries the operational (running) datastore on the mounted hosted.",
                context));
        apis.add(createGetApi("operations", "Queries the available operations (RPC calls) on the mounted hosted.",
                context));

        final ApiDeclaration declaration = swaggerGenerator.createApiDeclaration(
                swaggerGenerator.createBasePathFromUriInfo(uriInfo));
        declaration.setApis(apis);
        return declaration;

    }

    private Api createGetApi(final String datastore, final String note, final String context) {
        final Operation getConfig = new Operation();
        getConfig.setMethod("GET");
        getConfig.setNickname("GET " + datastore);
        getConfig.setNotes(note);

        final Api api = new Api();
        api.setPath(swaggerGenerator.getDataStorePath(datastore, context).concat(
                swaggerGenerator.getContent(datastore)));
        api.setOperations(Collections.singletonList(getConfig));

        return api;
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
