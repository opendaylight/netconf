/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionException;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.SimpleDOMActionResult;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.nb.rfc8040.Insert;
import org.opendaylight.restconf.nb.rfc8040.ReadDataParams;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.restconf.nb.rfc8040.databind.OperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.PatchBody;
import org.opendaylight.restconf.nb.rfc8040.databind.ResourceBody;
import org.opendaylight.restconf.nb.rfc8040.databind.jaxrs.QueryParams;
import org.opendaylight.restconf.nb.rfc8040.legacy.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.OperationsContent;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.OperationOutput;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.YangApi;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.restconf.Restconf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibrary;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RESTCONF server implemented on top of MD-SAL.
 */
// FIXME: this should live in 'org.opendaylight.restconf.server.mdsal' package
@Singleton
@Component(service = { MdsalRestconfServer.class, RestconfServer.class })
public final class MdsalRestconfServer implements RestconfServer {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalRestconfServer.class);
    private static final QName YANG_LIBRARY_VERSION = QName.create(Restconf.QNAME, "yang-library-version").intern();
    private static final String YANG_LIBRARY_REVISION = YangLibrary.QNAME.getRevision().orElseThrow().toString();
    private static final VarHandle LOCAL_STRATEGY;

    static {
        try {
            LOCAL_STRATEGY = MethodHandles.lookup()
                .findVarHandle(MdsalRestconfServer.class, "localStrategy", RestconfStrategy.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final @NonNull ImmutableMap<QName, RpcImplementation> localRpcs;
    private final @NonNull DOMMountPointService mountPointService;
    private final @NonNull DatabindProvider databindProvider;
    private final @NonNull DOMDataBroker dataBroker;
    private final @Nullable DOMRpcService rpcService;
    private final @Nullable DOMActionService actionService;

    @SuppressWarnings("unused")
    private volatile RestconfStrategy localStrategy;

    @Inject
    @Activate
    public MdsalRestconfServer(@Reference final DatabindProvider databindProvider,
            @Reference final DOMDataBroker dataBroker, @Reference final DOMRpcService rpcService,
            @Reference final DOMActionService actionService,
            @Reference final DOMMountPointService mountPointService,
            @Reference final List<RpcImplementation> localRpcs) {
        this.databindProvider = requireNonNull(databindProvider);
        this.dataBroker = requireNonNull(dataBroker);
        this.rpcService = requireNonNull(rpcService);
        this.actionService = requireNonNull(actionService);
        this.mountPointService = requireNonNull(mountPointService);
        this.localRpcs = Maps.uniqueIndex(localRpcs, RpcImplementation::qname);
    }

    public MdsalRestconfServer(final DatabindProvider databindProvider, final DOMDataBroker dataBroker,
            final DOMRpcService rpcService, final DOMActionService actionService,
            final DOMMountPointService mountPointService, final RpcImplementation... localRpcs) {
        this(databindProvider, dataBroker, rpcService, actionService, mountPointService, List.of(localRpcs));
    }

    @Override
    public RestconfFuture<Empty> dataDELETE(final String identifier) {
        final var reqPath = bindRequestPath(identifier);
        final var strategy = getRestconfStrategy(reqPath.getSchemaContext(), reqPath.getMountPoint());
        return strategy.delete(reqPath.getInstanceIdentifier());
    }

    @Override
    public RestconfFuture<NormalizedNodePayload> dataGET(final ReadDataParams readParams) {
        return readData(bindRequestRoot(), readParams);
    }

    @Override
    public RestconfFuture<NormalizedNodePayload> dataGET(final String identifier, final ReadDataParams readParams) {
        return readData(bindRequestPath(identifier), readParams);
    }

    private @NonNull RestconfFuture<NormalizedNodePayload> readData(final InstanceIdentifierContext reqPath,
            final ReadDataParams readParams) {
        final var queryParams = QueryParams.newQueryParameters(readParams, reqPath);
        final var fieldPaths = queryParams.fieldPaths();
        final var strategy = getRestconfStrategy(reqPath.getSchemaContext(), reqPath.getMountPoint());
        final NormalizedNode node;
        if (fieldPaths != null && !fieldPaths.isEmpty()) {
            node = strategy.readData(readParams.content(), reqPath.getInstanceIdentifier(),
                readParams.withDefaults(), fieldPaths);
        } else {
            node = strategy.readData(readParams.content(), reqPath.getInstanceIdentifier(),
                readParams.withDefaults());
        }
        if (node == null) {
            return RestconfFuture.failed(new RestconfDocumentedException(
                "Request could not be completed because the relevant data model content does not exist",
                ErrorType.PROTOCOL, ErrorTag.DATA_MISSING));
        }

        return RestconfFuture.of(new NormalizedNodePayload(reqPath.inference(), node, queryParams));
    }

    @Override
    public RestconfFuture<Empty> dataPATCH(final ResourceBody body) {
        return dataPATCH(bindRequestRoot(), body);
    }

    @Override
    public RestconfFuture<Empty> dataPATCH(final String identifier, final ResourceBody body) {
        return dataPATCH(bindRequestPath(identifier), body);
    }

    private @NonNull RestconfFuture<Empty> dataPATCH(final InstanceIdentifierContext reqPath, final ResourceBody body) {
        final var req = bindResourceRequest(reqPath, body);
        return req.strategy().merge(req.path(), req.data());
    }

    @Override
    public RestconfFuture<PatchStatusContext> dataPATCH(final PatchBody body) {
        return dataPATCH(bindRequestRoot(), body);
    }

    @Override
    public RestconfFuture<PatchStatusContext> dataPATCH(final String identifier, final PatchBody body) {
        return dataPATCH(bindRequestPath(identifier), body);
    }

    private @NonNull RestconfFuture<PatchStatusContext> dataPATCH(final InstanceIdentifierContext reqPath,
            final PatchBody body) {
        final var modelContext = reqPath.getSchemaContext();
        final PatchContext patch;
        try {
            patch = body.toPatchContext(modelContext, reqPath.getInstanceIdentifier());
        } catch (IOException e) {
            LOG.debug("Error parsing YANG Patch input", e);
            return RestconfFuture.failed(new RestconfDocumentedException("Error parsing input: " + e.getMessage(),
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, e));
        }
        return getRestconfStrategy(modelContext, reqPath.getMountPoint()).patchData(patch);
    }

    // FIXME: should follow the same pattern as operationsPOST() does
    RestconfFuture<DOMActionResult> dataInvokePOST(final InstanceIdentifierContext reqPath,
            final OperationInputBody body) {
        final var yangIIdContext = reqPath.getInstanceIdentifier();
        final var inference = reqPath.inference();
        final ContainerNode input;
        try {
            input = body.toContainerNode(inference);
        } catch (IOException e) {
            LOG.debug("Error reading input", e);
            return RestconfFuture.failed(new RestconfDocumentedException("Error parsing input: " + e.getMessage(),
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, e));
        }

        final var mountPoint = reqPath.getMountPoint();
        final var schemaPath = inference.toSchemaInferenceStack().toSchemaNodeIdentifier();
        return mountPoint != null ? dataInvokePOST(input, schemaPath, yangIIdContext, mountPoint)
            : dataInvokePOST(input, schemaPath, yangIIdContext, actionService);
    }

    /**
     * Invoke Action via ActionServiceHandler.
     *
     * @param data input data
     * @param yangIId invocation context
     * @param schemaPath schema path of data
     * @param actionService action service to invoke action
     * @return {@link DOMActionResult}
     */
    private static RestconfFuture<DOMActionResult> dataInvokePOST(final ContainerNode data, final Absolute schemaPath,
            final YangInstanceIdentifier yangIId, final DOMActionService actionService) {
        final var ret = new SettableRestconfFuture<DOMActionResult>();

        Futures.addCallback(actionService.invokeAction(schemaPath,
            new DOMDataTreeIdentifier(LogicalDatastoreType.OPERATIONAL, yangIId.getParent()), data),
            new FutureCallback<DOMActionResult>() {
                @Override
                public void onSuccess(final DOMActionResult result) {
                    final var errors = result.getErrors();
                    LOG.debug("InvokeAction Error Message {}", errors);
                    if (errors.isEmpty()) {
                        ret.set(result);
                    } else {
                        ret.setFailure(new RestconfDocumentedException("InvokeAction Error Message ", null, errors));
                    }
                }

                @Override
                public void onFailure(final Throwable cause) {
                    if (cause instanceof DOMActionException) {
                        ret.set(new SimpleDOMActionResult(List.of(RpcResultBuilder.newError(
                            ErrorType.RPC, ErrorTag.OPERATION_FAILED, cause.getMessage()))));
                    } else if (cause instanceof RestconfDocumentedException e) {
                        ret.setFailure(e);
                    } else if (cause instanceof CancellationException) {
                        ret.setFailure(new RestconfDocumentedException("Action cancelled while executing",
                            ErrorType.RPC, ErrorTag.PARTIAL_OPERATION, cause));
                    } else {
                        ret.setFailure(new RestconfDocumentedException("Invocation failed", cause));
                    }
                }
            }, MoreExecutors.directExecutor());

        return ret;
    }

    /**
     * Invoking Action via mount point.
     *
     * @param mountPoint mount point
     * @param data input data
     * @param schemaPath schema path of data
     * @return {@link DOMActionResult}
     */
    private static RestconfFuture<DOMActionResult> dataInvokePOST(final ContainerNode data, final Absolute schemaPath,
            final YangInstanceIdentifier yangIId, final DOMMountPoint mountPoint) {
        final var actionService = mountPoint.getService(DOMActionService.class);
        return actionService.isPresent() ? dataInvokePOST(data, schemaPath, yangIId, actionService.orElseThrow())
            : RestconfFuture.failed(new RestconfDocumentedException("DOMActionService is missing."));
    }

    @Override
    public RestconfFuture<DataPutResult> dataPUT(final ResourceBody body, final Map<String, String> query) {
        return dataPUT(bindRequestRoot(), body, query);
    }

    @Override
    public RestconfFuture<DataPutResult> dataPUT(final String identifier, final ResourceBody body,
             final Map<String, String> queryParameters) {
        return dataPUT(bindRequestPath(identifier), body, queryParameters);
    }

    private @NonNull RestconfFuture<DataPutResult> dataPUT(final InstanceIdentifierContext reqPath,
            final ResourceBody body, final Map<String, String> queryParameters) {
        final Insert insert;
        try {
            insert = Insert.ofQueryParameters(reqPath.getSchemaContext(), queryParameters);
        } catch (IllegalArgumentException e) {
            return RestconfFuture.failed(new RestconfDocumentedException(e.getMessage(),
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e));
        }
        final var req = bindResourceRequest(reqPath, body);
        return req.strategy().putData(req.path(), req.data(), insert);
    }

    @Override
    public OperationsContent operationsGET() {
        return operationsGET(databindProvider.currentContext().modelContext());
    }

    @Override
    public OperationsContent operationsGET(final String operation) {
        // get current module RPCs/actions by RPC/action name
        final var inference = bindRequestPath(operation).inference();
        if (inference.isEmpty()) {
            return operationsGET(inference.getEffectiveModelContext());
        }

        final var stmt = inference.toSchemaInferenceStack().currentStatement();
        if (stmt instanceof RpcEffectiveStatement rpc) {
            return new OperationsContent.Leaf(inference.getEffectiveModelContext(), rpc.argument());
        }
        LOG.debug("Operation '{}' resulted in non-RPC {}", operation, stmt);
        return null;
    }

    private static @NonNull OperationsContent operationsGET(final EffectiveModelContext modelContext) {
        final var modules = modelContext.getModuleStatements();
        if (modules.isEmpty()) {
            // No modules, or defensive return empty content
            return new OperationsContent.Container(modelContext, ImmutableSetMultimap.of());
        }

        // RPCs by their XMLNamespace/Revision
        final var table = HashBasedTable.<XMLNamespace, Revision, ImmutableSet<QName>>create();
        for (var entry : modules.entrySet()) {
            final var module = entry.getValue();
            final var rpcNames = module.streamEffectiveSubstatements(RpcEffectiveStatement.class)
                .map(RpcEffectiveStatement::argument)
                .collect(ImmutableSet.toImmutableSet());
            if (!rpcNames.isEmpty()) {
                final var namespace = entry.getKey();
                table.put(namespace.getNamespace(), namespace.getRevision().orElse(null), rpcNames);
            }
        }

        // Now pick the latest revision for each namespace
        final var rpcs = ImmutableSetMultimap.<QNameModule, QName>builder();
        for (var entry : table.rowMap().entrySet()) {
            entry.getValue().entrySet().stream()
                .sorted(Comparator.comparing(Entry::getKey, (first, second) -> Revision.compare(second, first)))
                .findFirst()
                .ifPresent(row -> rpcs.putAll(QNameModule.create(entry.getKey(), row.getKey()), row.getValue()));
        }
        return new OperationsContent.Container(modelContext, rpcs.build());
    }

    @Override
    public RestconfFuture<OperationOutput> operationsPOST(final URI restconfURI, final String apiPath,
            final OperationInputBody body) {
        final var currentContext = databindProvider.currentContext();
        final var reqPath = bindRequestPath(currentContext, apiPath);
        final var inference = reqPath.inference();
        final ContainerNode input;
        try {
            input = body.toContainerNode(inference);
        } catch (IOException e) {
            LOG.debug("Error reading input", e);
            return RestconfFuture.failed(new RestconfDocumentedException("Error parsing input: " + e.getMessage(),
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, e));
        }

        return getRestconfStrategy(reqPath.getSchemaContext(), reqPath.getMountPoint())
            .invokeRpc(restconfURI, reqPath.getSchemaNode().getQName(),
                new OperationInput(currentContext, inference, input));
    }

    @Override
    public NormalizedNodePayload yangLibraryVersionGET() {
        final var stack = SchemaInferenceStack.of(databindProvider.currentContext().modelContext());
        stack.enterYangData(YangApi.NAME);
        stack.enterDataTree(Restconf.QNAME);
        stack.enterDataTree(YANG_LIBRARY_VERSION);
        return new NormalizedNodePayload(stack.toInference(),
            ImmutableNodes.leafNode(YANG_LIBRARY_VERSION, YANG_LIBRARY_REVISION));
    }

    @NonNull InstanceIdentifierContext bindRequestPath(final String identifier) {
        return bindRequestPath(databindProvider.currentContext(), identifier);
    }

    @Deprecated
    @NonNull InstanceIdentifierContext bindRequestPath(final DatabindContext databind, final String identifier) {
        // FIXME: go through ApiPath first. That part should eventually live in callers
        // FIXME: DatabindContext looks like it should be internal
        return verifyNotNull(ParserIdentifier.toInstanceIdentifier(requireNonNull(identifier), databind.modelContext(),
            mountPointService));
    }

    @NonNull InstanceIdentifierContext bindRequestRoot() {
        return InstanceIdentifierContext.ofLocalRoot(databindProvider.currentContext().modelContext());
    }

    @NonNull ResourceRequest bindResourceRequest(final InstanceIdentifierContext reqPath, final ResourceBody body) {
        final var inference = reqPath.inference();
        final var path = reqPath.getInstanceIdentifier();
        final var data = body.toNormalizedNode(path, inference, reqPath.getSchemaNode());

        return new ResourceRequest(
            getRestconfStrategy(inference.getEffectiveModelContext(), reqPath.getMountPoint()), path, data);
    }

    @VisibleForTesting
    @NonNull RestconfStrategy getRestconfStrategy(final EffectiveModelContext modelContext,
            final @Nullable DOMMountPoint mountPoint) {
        if (mountPoint == null) {
            return localStrategy(modelContext);
        }

        final var ret = RestconfStrategy.forMountPoint(modelContext, mountPoint);
        if (ret == null) {
            final var mountId = mountPoint.getIdentifier();
            LOG.warn("Mount point {} does not expose a suitable access interface", mountId);
            throw new RestconfDocumentedException("Could not find a supported access interface in mount point "
                + mountId);
        }
        return ret;
    }

    private @NonNull RestconfStrategy localStrategy(final EffectiveModelContext modelContext) {
        final var local = (RestconfStrategy) LOCAL_STRATEGY.getAcquire(this);
        if (local != null && modelContext.equals(local.modelContext())) {
            return local;
        }

        final var created = new MdsalRestconfStrategy(modelContext, dataBroker, rpcService, localRpcs);
        LOCAL_STRATEGY.setRelease(this, created);
        return created;
    }
}
