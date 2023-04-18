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
import static org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.DEFAULT_PAGESIZE;
import static org.opendaylight.netconf.sal.rest.doc.impl.BaseYangSwaggerGenerator.BASE_PATH;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.DESCRIPTION_KEY;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.RESPONSES_KEY;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.SUMMARY_KEY;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.SUMMARY_SEPARATOR;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.TAGS_KEY;
import static org.opendaylight.netconf.sal.rest.doc.model.builder.OperationBuilder.buildTagsValue;
import static org.opendaylight.netconf.sal.rest.doc.util.JsonUtil.addFields;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Range;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.dom.api.DOMMountPointListener;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.OAversion;
import org.opendaylight.netconf.sal.rest.doc.impl.BaseYangSwaggerGenerator;
import org.opendaylight.netconf.sal.rest.doc.impl.DefinitionNames;
import org.opendaylight.netconf.sal.rest.doc.swagger.CommonApiObject;
import org.opendaylight.netconf.sal.rest.doc.swagger.SwaggerObject;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
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
        synchronized (lock) {
            final SchemaContext context = globalSchema.getGlobalContext();
            for (final Entry<YangInstanceIdentifier, Long> entry : instanceIdToLongId.entrySet()) {
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
        final String modName = findModuleName(key, globalSchema.getGlobalContext());
        return swaggerGenerator.generateUrlPrefixFromInstanceID(key, modName) + "yang-ext:mount";
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

    public CommonApiObject getMountPointApi(final UriInfo uriInfo, final Long id, final String module,
            final String revision, final OAversion oaversion) {
        final YangInstanceIdentifier iid = getInstanceId(id);
        final EffectiveModelContext context = getSchemaContext(iid);
        final String urlPrefix = getYangMountUrl(iid);
        final String deviceName  = extractDeviceName(iid);

        if (context == null) {
            return null;
        }

        if (DATASTORES_LABEL.equals(module) && DATASTORES_REVISION.equals(revision)) {
            return generateDataStoreApiDoc(uriInfo, urlPrefix, deviceName);
        }
        final SwaggerObject swaggerObject = swaggerGenerator.getApiDeclaration(module, revision, uriInfo, context,
                urlPrefix, oaversion);
        return BaseYangSwaggerGenerator.getAppropriateDoc(swaggerObject, oaversion);
    }

    public CommonApiObject getMountPointApi(final UriInfo uriInfo, final Long id, final Optional<Integer> pageNum,
            final OAversion oaversion) {
        final YangInstanceIdentifier iid = getInstanceId(id);
        final EffectiveModelContext context = getSchemaContext(iid);
        final String urlPrefix = getYangMountUrl(iid);
        final String deviceName  = extractDeviceName(iid);

        if (context == null) {
            return null;
        }
        final DefinitionNames definitionNames = new DefinitionNames();

        boolean includeDataStore = true;
        Optional<Range<Integer>> range = Optional.empty();

        if (pageNum.isPresent()) {
            final int pageNumValue = pageNum.orElseThrow();
            final int end = DEFAULT_PAGESIZE * pageNumValue - 1;
            int start = end - DEFAULT_PAGESIZE;
            if (pageNumValue == 1) {
                start++;
            } else {
                includeDataStore = false;
            }
            range = Optional.of(Range.closed(start, end));
        }

        final SwaggerObject doc;

        final SwaggerObject swaggerObject = swaggerGenerator.getAllModulesDoc(uriInfo, range, context,
                Optional.of(deviceName), urlPrefix, definitionNames, oaversion);

        if (includeDataStore) {
            doc = generateDataStoreApiDoc(uriInfo, urlPrefix, deviceName);
            addFields(doc.getPaths() ,swaggerObject.getPaths().fields());
            addFields(doc.getDefinitions() ,swaggerObject.getDefinitions().fields());
            doc.getInfo().setTitle(swaggerObject.getInfo().getTitle());
        } else {
            doc = swaggerObject;
        }

        return BaseYangSwaggerGenerator.getAppropriateDoc(doc, oaversion);
    }

    private static String extractDeviceName(final YangInstanceIdentifier iid) {
        return ((YangInstanceIdentifier.NodeIdentifierWithPredicates.Singleton)iid.getLastPathArgument())
                .values().getElement().toString();
    }

    private SwaggerObject generateDataStoreApiDoc(final UriInfo uriInfo, final String context,
            final String deviceName) {
        final SwaggerObject declaration = swaggerGenerator.createSwaggerObject(
                swaggerGenerator.createSchemaFromUriInfo(uriInfo),
                swaggerGenerator.createHostFromUriInfo(uriInfo),
                BASE_PATH,
                context);

        final ObjectNode pathsObject = JsonNodeFactory.instance.objectNode();
        createGetPathItem("data", "Queries the config (startup) datastore on the mounted hosted.",
                context, deviceName, pathsObject);
        createGetPathItem("operations", "Queries the available operations (RPC calls) on the mounted hosted.",
                context, deviceName, pathsObject);

        declaration.setPaths(pathsObject);
        declaration.setDefinitions(JsonNodeFactory.instance.objectNode());

        return declaration;
    }

    private void createGetPathItem(final String resourceType, final String description, final String context,
            final String deviceName, final ObjectNode pathsObject) {
        final ObjectNode pathItem = JsonNodeFactory.instance.objectNode();
        final ObjectNode operationObject = JsonNodeFactory.instance.objectNode();
        pathItem.set("get", operationObject);
        operationObject.put(DESCRIPTION_KEY, description);
        operationObject.put(SUMMARY_KEY, HttpMethod.GET + SUMMARY_SEPARATOR + deviceName + SUMMARY_SEPARATOR
                + resourceType);
        operationObject.set(TAGS_KEY, buildTagsValue(Optional.of(deviceName), "GET root"));
        final ObjectNode okResponse = JsonNodeFactory.instance.objectNode();
        okResponse.put(DESCRIPTION_KEY, Response.Status.OK.getReasonPhrase());
        final ObjectNode responses = JsonNodeFactory.instance.objectNode();
        responses.set(String.valueOf(Response.Status.OK.getStatusCode()), okResponse);
        operationObject.set(RESPONSES_KEY, responses);
        pathsObject.set(swaggerGenerator.getResourcePath(resourceType, context), pathItem);
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
