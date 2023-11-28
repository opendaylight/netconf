/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static com.google.common.base.Verify.verifyNotNull;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Splitter;
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
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import javax.annotation.PreDestroy;
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
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMYangTextSourceProvider;
import org.opendaylight.mdsal.dom.spi.SimpleDOMActionResult;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.nb.rfc8040.Insert;
import org.opendaylight.restconf.nb.rfc8040.databind.ChildBody;
import org.opendaylight.restconf.nb.rfc8040.databind.DataPostBody;
import org.opendaylight.restconf.nb.rfc8040.databind.OperationInputBody;
import org.opendaylight.restconf.nb.rfc8040.databind.PatchBody;
import org.opendaylight.restconf.nb.rfc8040.databind.ResourceBody;
import org.opendaylight.restconf.nb.rfc8040.legacy.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.legacy.QueryParameters;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy.StrategyAndTail;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.NetconfFieldsTranslator;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.WriterFieldsTranslator;
import org.opendaylight.restconf.server.api.DataGetParams;
import org.opendaylight.restconf.server.api.DataPatchPath;
import org.opendaylight.restconf.server.api.DataPostPath;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.DataPostResult.CreateResource;
import org.opendaylight.restconf.server.api.DataPostResult.InvokeOperation;
import org.opendaylight.restconf.server.api.DataPutPath;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.ModulesGetResult;
import org.opendaylight.restconf.server.api.OperationsGetResult;
import org.opendaylight.restconf.server.api.OperationsPostPath;
import org.opendaylight.restconf.server.api.OperationsPostResult;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.DatabindProvider;
import org.opendaylight.restconf.server.spi.OperationInput;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.YangApi;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.restconf.Restconf;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.common.YangNames;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.ActionDefinition;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.opendaylight.yangtools.yang.model.api.stmt.RpcEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.api.YinTextSchemaSource;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RESTCONF server implemented on top of MD-SAL.
 */
@Singleton
@Component(service = { RestconfServer.class, DatabindProvider.class })
public final class MdsalRestconfServer
        implements RestconfServer, DatabindProvider, EffectiveModelContextListener, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalRestconfServer.class);
    private static final QName YANG_LIBRARY_VERSION = QName.create(Restconf.QNAME, "yang-library-version").intern();
    private static final VarHandle LOCAL_STRATEGY;

    static {
        try {
            LOCAL_STRATEGY = MethodHandles.lookup()
                .findVarHandle(MdsalRestconfServer.class, "localStrategy", MdsalRestconfStrategy.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // FIXME: Remove this constant. All logic relying on this constant should instead rely on YangInstanceIdentifier
    //        equivalent coming out of argument parsing. This may require keeping List<YangInstanceIdentifier> as the
    //        nested path split on yang-ext:mount. This splitting needs to be based on consulting the
    //        EffectiveModelContext and allowing it only where yang-ext:mount is actually used in models.
    @Deprecated(forRemoval = true)
    private static final String MOUNT = "yang-ext:mount";
    @Deprecated(forRemoval = true)
    private static final Splitter SLASH_SPLITTER = Splitter.on('/');

    private final @NonNull ImmutableMap<QName, RpcImplementation> localRpcs;
    private final @NonNull DOMMountPointService mountPointService;
    private final @NonNull DOMDataBroker dataBroker;
    private final @Nullable DOMRpcService rpcService;
    private final @Nullable DOMActionService actionService;
    private final @Nullable DOMYangTextSourceProvider sourceProvider;

    private final Registration reg;

    @SuppressWarnings("unused")
    private volatile MdsalRestconfStrategy localStrategy;

    @Inject
    @Activate
    public MdsalRestconfServer(@Reference final DOMSchemaService schemaService,
            @Reference final DOMDataBroker dataBroker, @Reference final DOMRpcService rpcService,
            @Reference final DOMActionService actionService,
            @Reference final DOMMountPointService mountPointService,
            @Reference final List<RpcImplementation> localRpcs) {
        this.dataBroker = requireNonNull(dataBroker);
        this.rpcService = requireNonNull(rpcService);
        this.actionService = requireNonNull(actionService);
        this.mountPointService = requireNonNull(mountPointService);
        this.localRpcs = Maps.uniqueIndex(localRpcs, RpcImplementation::qname);
        sourceProvider = schemaService.getExtensions().getInstance(DOMYangTextSourceProvider.class);

        localStrategy = createLocalStrategy(schemaService.getGlobalContext());
        reg = schemaService.registerSchemaContextListener(this);
    }

    public MdsalRestconfServer(final DOMSchemaService schemaService, final DOMDataBroker dataBroker,
            final DOMRpcService rpcService, final DOMActionService actionService,
            final DOMMountPointService mountPointService, final RpcImplementation... localRpcs) {
        this(schemaService, dataBroker, rpcService, actionService, mountPointService, List.of(localRpcs));
    }

    @Override
    public DatabindContext currentDatabind() {
        return localStrategy().databind();
    }

    @Override
    public void onModelContextUpdated(final EffectiveModelContext newModelContext) {
        final var local = localStrategy();
        if (!newModelContext.equals(local.modelContext())) {
            LOCAL_STRATEGY.setRelease(this, createLocalStrategy(newModelContext));
        }
    }

    private @NonNull MdsalRestconfStrategy createLocalStrategy(final EffectiveModelContext modelContext) {
        return new MdsalRestconfStrategy(DatabindContext.ofModel(modelContext), dataBroker, rpcService, sourceProvider,
            mountPointService, localRpcs);
    }

    private @NonNull MdsalRestconfStrategy localStrategy() {
        return verifyNotNull((MdsalRestconfStrategy) LOCAL_STRATEGY.getAcquire(this));
    }

    @Deprecated(forRemoval = true)
    private @NonNull MdsalRestconfStrategy localStrategy(final DatabindContext databind) {
        final var local = localStrategy();
        return local.databind().equals(databind) ? local
            : new MdsalRestconfStrategy(databind, dataBroker, rpcService, sourceProvider, mountPointService, localRpcs);
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        reg.close();
        localStrategy = null;
    }

    @Override
    public RestconfFuture<Empty> dataDELETE(final ApiPath identifier) {
        final var stratAndPath = localStrategy().resolveStrategyPath(identifier);
        return stratAndPath.strategy().delete(stratAndPath.instance());
    }

    @Override
    public RestconfFuture<NormalizedNodePayload> dataGET(final DataGetParams params) {
        return readData(bindRequestRoot(), params);
    }

    @Override
    public RestconfFuture<NormalizedNodePayload> dataGET(final ApiPath identifier, final DataGetParams params) {
        return readData(bindRequestPath(identifier), params);
    }

    private @NonNull RestconfFuture<NormalizedNodePayload> readData(final InstanceIdentifierContext reqPath,
            final DataGetParams params) {
        final var fields = params.fields();
        final QueryParameters queryParams;
        if (fields != null) {
            final var modelContext = reqPath.databind().modelContext();
            final var schemaNode = (DataSchemaNode) reqPath.getSchemaNode();
            if (reqPath.getMountPoint() != null) {
                queryParams = QueryParameters.ofFieldPaths(params, NetconfFieldsTranslator.translate(modelContext,
                    schemaNode, fields));
            } else {
                queryParams = QueryParameters.ofFields(params, WriterFieldsTranslator.translate(modelContext,
                    schemaNode, fields));
            }
        } else {
            queryParams = QueryParameters.of(params);
        }

        final var fieldPaths = queryParams.fieldPaths();
        final var strategy = getRestconfStrategy(reqPath.databind(), reqPath.getMountPoint());
        final NormalizedNode node;
        if (fieldPaths != null && !fieldPaths.isEmpty()) {
            node = strategy.readData(params.content(), reqPath.getInstanceIdentifier(), params.withDefaults(),
                fieldPaths);
        } else {
            node = strategy.readData(params.content(), reqPath.getInstanceIdentifier(), params.withDefaults());
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
    public RestconfFuture<Empty> dataPATCH(final ApiPath identifier, final ResourceBody body) {
        return dataPATCH(bindRequestPath(identifier), body);
    }

    private @NonNull RestconfFuture<Empty> dataPATCH(final InstanceIdentifierContext reqPath, final ResourceBody body) {
        final var req = bindResourceRequest(reqPath, body);
        return req.strategy().merge(req.path(), req.data());
    }

    @Override
    public RestconfFuture<PatchStatusContext> dataPATCH(final PatchBody body) {
        final var strategy = localStrategy();
        return dataPATCH(strategy, new DataPatchPath(strategy.databind(), YangInstanceIdentifier.of()), body);
    }

    @Override
    public RestconfFuture<PatchStatusContext> dataPATCH(final ApiPath identifier, final PatchBody body) {
        final var stratAndPath = localStrategy().resolveStrategyPath(identifier);
        final var strategy = stratAndPath.strategy();
        return dataPATCH(strategy, new DataPatchPath(strategy.databind(), stratAndPath.instance()), body);
    }

    private static @NonNull RestconfFuture<PatchStatusContext> dataPATCH(final RestconfStrategy strategy,
            final DataPatchPath path, final PatchBody body) {
        final PatchContext patch;
        try {
            patch = body.toPatchContext(path);
        } catch (IOException e) {
            LOG.debug("Error parsing YANG Patch input", e);
            return RestconfFuture.failed(new RestconfDocumentedException("Error parsing input: " + e.getMessage(),
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, e));
        }
        return strategy.patchData(patch);
    }

    @Override
    public RestconfFuture<CreateResource> dataPOST(final ChildBody body, final Map<String, String> queryParameters) {
        return dataCreatePOST(bindRequestRoot(), body, queryParameters);
    }

    @Override
    public RestconfFuture<? extends DataPostResult> dataPOST(final ApiPath identifier, final DataPostBody body,
            final Map<String, String> queryParameters) {
        final var reqPath = bindRequestPath(identifier);
        if (reqPath.getSchemaNode() instanceof ActionDefinition) {
            try (var inputBody = body.toOperationInput()) {
                return dataInvokePOST(reqPath, inputBody);
            }
        }

        try (var childBody = body.toResource()) {
            return dataCreatePOST(reqPath, childBody, queryParameters);
        }
    }

    private @NonNull RestconfFuture<CreateResource> dataCreatePOST(final InstanceIdentifierContext reqPath,
            final ChildBody body, final Map<String, String> queryParameters) {
        final var postPath = new DataPostPath(reqPath.databind(), reqPath.inference(), reqPath.getInstanceIdentifier());

        final Insert insert;
        try {
            insert = Insert.ofQueryParameters(postPath.databind(), queryParameters);
        } catch (IllegalArgumentException e) {
            return RestconfFuture.failed(new RestconfDocumentedException(e.getMessage(),
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e));
        }

        final var payload = body.toPayload(postPath);
        return getRestconfStrategy(reqPath.databind(), reqPath.getMountPoint())
            .postData(concat(postPath.instance(), payload.prefix()), payload.body(), insert);
    }

    private static YangInstanceIdentifier concat(final YangInstanceIdentifier parent, final List<PathArgument> args) {
        var ret = parent;
        for (var arg : args) {
            ret = ret.node(arg);
        }
        return ret;
    }

    private RestconfFuture<InvokeOperation> dataInvokePOST(final InstanceIdentifierContext reqPath,
            final OperationInputBody body) {
        final var postPath = new OperationsPostPath(reqPath.databind(), reqPath.inference());
        final var yangIIdContext = reqPath.getInstanceIdentifier();
        final ContainerNode input;
        try {
            input = body.toContainerNode(postPath);
        } catch (IOException e) {
            LOG.debug("Error reading input", e);
            return RestconfFuture.failed(new RestconfDocumentedException("Error parsing input: " + e.getMessage(),
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, e));
        }

        final var mountPoint = reqPath.getMountPoint();
        final var schemaPath = postPath.operation().toSchemaInferenceStack().toSchemaNodeIdentifier();
        final var future = mountPoint != null ? dataInvokePOST(input, schemaPath, yangIIdContext, mountPoint)
            : dataInvokePOST(input, schemaPath, yangIIdContext, actionService);

        return future.transform(result -> result.getOutput()
            .flatMap(output -> output.isEmpty() ? Optional.empty()
                : Optional.of(new InvokeOperation(new NormalizedNodePayload(reqPath.inference(), output))))
            .orElse(InvokeOperation.EMPTY));
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
    public RestconfFuture<DataPutResult> dataPUT(final ApiPath identifier, final ResourceBody body,
             final Map<String, String> queryParameters) {
        return dataPUT(bindRequestPath(identifier), body, queryParameters);
    }

    private @NonNull RestconfFuture<DataPutResult> dataPUT(final InstanceIdentifierContext reqPath,
            final ResourceBody body, final Map<String, String> queryParameters) {
        final Insert insert;
        try {
            insert = Insert.ofQueryParameters(reqPath.databind(), queryParameters);
        } catch (IllegalArgumentException e) {
            return RestconfFuture.failed(new RestconfDocumentedException(e.getMessage(),
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e));
        }
        final var req = bindResourceRequest(reqPath, body);
        return req.strategy().putData(req.path(), req.data(), insert);
    }

    @Override
    public RestconfFuture<ModulesGetResult> modulesYangGET(final String fileName, final String revision) {
        return modulesGET(fileName, revision, YangTextSchemaSource.class);
    }

    @Override
    public RestconfFuture<ModulesGetResult> modulesYangGET(final ApiPath mountPath, final String fileName,
            final String revision) {
        return modulesGET(mountPath, fileName, revision, YangTextSchemaSource.class);
    }

    @Override
    public RestconfFuture<ModulesGetResult> modulesYinGET(final String fileName, final String revision) {
        return modulesGET(fileName, revision, YinTextSchemaSource.class);
    }

    @Override
    public RestconfFuture<ModulesGetResult> modulesYinGET(final ApiPath mountPath, final String fileName,
            final String revision) {
        return modulesGET(mountPath, fileName, revision, YinTextSchemaSource.class);
    }

    private @NonNull RestconfFuture<ModulesGetResult> modulesGET(final String fileName, final String revision,
            final Class<? extends SchemaSourceRepresentation> representation) {
        return modulesGET(localStrategy(), fileName, revision, representation);
    }

    private @NonNull RestconfFuture<ModulesGetResult> modulesGET(final ApiPath mountPath, final String fileName,
            final String revision, final Class<? extends SchemaSourceRepresentation> representation) {
        final var mountOffset = mountPath.indexOf("yang-ext", "mount");
        if (mountOffset != mountPath.steps().size() - 1) {
            return RestconfFuture.failed(new RestconfDocumentedException("Mount path has to end with yang-ext:mount"));
        }

        final StrategyAndTail stratAndTail;
        try {
            stratAndTail = localStrategy().resolveStrategy(mountPath);
        } catch (RestconfDocumentedException e) {
            return RestconfFuture.failed(e);
        }
        // FIXME: require remnant to be empty
        return modulesGET(stratAndTail.strategy(), fileName, revision, representation);
    }

    private static @NonNull RestconfFuture<ModulesGetResult> modulesGET(final RestconfStrategy strategy,
            final String moduleName, final String revisionStr,
            final Class<? extends SchemaSourceRepresentation> representation) {
        if (moduleName == null) {
            return RestconfFuture.failed(new RestconfDocumentedException("Module name must be supplied",
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE));
        }
        if (moduleName.isEmpty() || !YangNames.IDENTIFIER_START.matches(moduleName.charAt(0))) {
            return RestconfFuture.failed(new RestconfDocumentedException(
                "Identifier must start with character from set 'a-zA-Z_", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE));
        }
        if (moduleName.toUpperCase(Locale.ROOT).startsWith("XML")) {
            return RestconfFuture.failed(new RestconfDocumentedException(
                "Identifier must NOT start with XML ignore case", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE));
        }
        if (YangNames.NOT_IDENTIFIER_PART.matchesAnyOf(moduleName.substring(1))) {
            return RestconfFuture.failed(new RestconfDocumentedException(
                "Supplied name has not expected identifier format", ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE));
        }

        // YANG Revision-compliant string is required
        final Revision revision;
        try {
            revision = Revision.ofNullable(revisionStr).orElse(null);
        } catch (final DateTimeParseException e) {
            return RestconfFuture.failed(new RestconfDocumentedException(
                "Supplied revision is not in expected date format YYYY-mm-dd",
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE, e));
        }

        return strategy.resolveSource(new SourceIdentifier(moduleName, revision), representation)
            .transform(ModulesGetResult::new);
    }

    @Override
    public RestconfFuture<OperationsGetResult> operationsGET() {
        return operationsGET(localStrategy().modelContext());
    }

    @Override
    public RestconfFuture<OperationsGetResult> operationsGET(final ApiPath operation) {
        // get current module RPCs/actions by RPC/action name
        final var inference = bindRequestPath(operation).inference();
        if (inference.isEmpty()) {
            return operationsGET(inference.getEffectiveModelContext());
        }

        final var stmt = inference.toSchemaInferenceStack().currentStatement();
        if (stmt instanceof RpcEffectiveStatement rpc) {
            return RestconfFuture.of(
                new OperationsGetResult.Leaf(inference.getEffectiveModelContext(), rpc.argument()));
        }
        return RestconfFuture.failed(new RestconfDocumentedException("RPC not found",
            ErrorType.PROTOCOL, ErrorTag.DATA_MISSING));
    }

    private static @NonNull RestconfFuture<OperationsGetResult> operationsGET(
            final EffectiveModelContext modelContext) {
        final var modules = modelContext.getModuleStatements();
        if (modules.isEmpty()) {
            // No modules, or defensive return empty content
            return RestconfFuture.of(new OperationsGetResult.Container(modelContext, ImmutableSetMultimap.of()));
        }

        // RPC QNames by their XMLNamespace/Revision. This should be a Table, but Revision can be null, which wrecks us.
        final var table = new HashMap<XMLNamespace, Map<Revision, ImmutableSet<QName>>>();
        for (var entry : modules.entrySet()) {
            final var module = entry.getValue();
            final var rpcNames = module.streamEffectiveSubstatements(RpcEffectiveStatement.class)
                .map(RpcEffectiveStatement::argument)
                .collect(ImmutableSet.toImmutableSet());
            if (!rpcNames.isEmpty()) {
                final var namespace = entry.getKey();
                table.computeIfAbsent(namespace.getNamespace(), ignored -> new HashMap<>())
                    .put(namespace.getRevision().orElse(null), rpcNames);
            }
        }

        // Now pick the latest revision for each namespace
        final var rpcs = ImmutableSetMultimap.<QNameModule, QName>builder();
        for (var entry : table.entrySet()) {
            entry.getValue().entrySet().stream()
                .sorted(Comparator.comparing(Entry::getKey, (first, second) -> Revision.compare(second, first)))
                .findFirst()
                .ifPresent(row -> rpcs.putAll(QNameModule.create(entry.getKey(), row.getKey()), row.getValue()));
        }
        return RestconfFuture.of(new OperationsGetResult.Container(modelContext, rpcs.build()));
    }

    @Override
    public RestconfFuture<OperationsPostResult> operationsPOST(final URI restconfURI, final ApiPath apiPath,
            final OperationInputBody body) {
        final var reqPath = bindRequestPath(localStrategy(), apiPath);
        final var postPath = new OperationsPostPath(reqPath.databind(), reqPath.inference());

        final ContainerNode input;
        try {
            input = body.toContainerNode(postPath);
        } catch (IOException e) {
            LOG.debug("Error reading input", e);
            return RestconfFuture.failed(new RestconfDocumentedException("Error parsing input: " + e.getMessage(),
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, e));
        }

        final var strategy = getRestconfStrategy(reqPath.databind(), reqPath.getMountPoint());
        return strategy.invokeRpc(restconfURI, reqPath.getSchemaNode().getQName(),
                new OperationInput(strategy.databind(), postPath.operation(), input));
    }

    @Override
    public RestconfFuture<NormalizedNodePayload> yangLibraryVersionGET() {
        final var stack = SchemaInferenceStack.of(localStrategy().modelContext());
        try {
            stack.enterYangData(YangApi.NAME);
            stack.enterDataTree(Restconf.QNAME);
            stack.enterDataTree(YANG_LIBRARY_VERSION);
        } catch (IllegalArgumentException e) {
            return RestconfFuture.failed(new RestconfDocumentedException("RESTCONF is not available"));
        }
        return RestconfFuture.of(new NormalizedNodePayload(stack.toInference(),
            ImmutableNodes.leafNode(YANG_LIBRARY_VERSION, stack.getEffectiveModelContext()
                .findModuleStatements("ietf-yang-library").iterator().next().localQNameModule().getRevision()
                .map(Revision::toString).orElse(""))));
    }

//    private @NonNull InstanceIdentifierContext bindRequestPath(final @NonNull ApiPath identifier) {
//        return bindRequestPath(localStrategy(), identifier);
//    }

//    private @NonNull InstanceIdentifierContext bindRequestPath(final @NonNull MdsalRestconfStrategy strategy,
//            final @NonNull ApiPath identifier) {
//        // FIXME: DatabindContext looks like it should be internal
//        return InstanceIdentifierContext.ofApiPath(identifier, strategy.databind(), mountPointService);
//    }

    private @NonNull InstanceIdentifierContext bindRequestRoot() {
        return InstanceIdentifierContext.ofLocalRoot(localStrategy().databind());
    }

    private @NonNull ResourceRequest bindResourceRequest(final InstanceIdentifierContext reqPath,
            final ResourceBody body) {
        final var putPath = new DataPutPath(reqPath.databind(), reqPath.inference(), reqPath.getInstanceIdentifier());
        return new ResourceRequest(getRestconfStrategy(putPath.databind(), reqPath.getMountPoint()), putPath.instance(),
            body.toNormalizedNode(putPath));
    }

//    @VisibleForTesting
//    @NonNull RestconfStrategy getRestconfStrategy(final DatabindContext databind,
//            final @Nullable DOMMountPoint mountPoint) {
//        if (mountPoint == null) {
//            return localStrategy(databind);
//        }
//        return forMountPoint(databind, mountPoint);
//    }
}
