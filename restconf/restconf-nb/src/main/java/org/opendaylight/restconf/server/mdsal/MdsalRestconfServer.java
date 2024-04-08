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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.URI;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy.StrategyAndTail;
import org.opendaylight.restconf.server.api.ChildBody;
import org.opendaylight.restconf.server.api.CreateResourceResult;
import org.opendaylight.restconf.server.api.DataGetResult;
import org.opendaylight.restconf.server.api.DataPatchResult;
import org.opendaylight.restconf.server.api.DataPostBody;
import org.opendaylight.restconf.server.api.DataPostResult;
import org.opendaylight.restconf.server.api.DataPutResult;
import org.opendaylight.restconf.server.api.DataYangPatchResult;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.ModulesGetResult;
import org.opendaylight.restconf.server.api.OperationInputBody;
import org.opendaylight.restconf.server.api.PatchBody;
import org.opendaylight.restconf.server.api.QueryParams;
import org.opendaylight.restconf.server.api.ResourceBody;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.RestconfServerConfiguration;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.common.YangNames;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.SourceRepresentation;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.api.source.YinTextSource;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;

/**
 * A RESTCONF server implemented on top of MD-SAL.
 */
@Singleton
@Component(service = RestconfServer.class, configurationPid = "org.opendaylight.restconf.server")
@Designate(ocd = RestconfServerConfiguration.class)
public final class MdsalRestconfServer implements RestconfServer, AutoCloseable {
    private static final VarHandle LOCAL_STRATEGY;

    static {
        try {
            LOCAL_STRATEGY = MethodHandles.lookup()
                .findVarHandle(MdsalRestconfServer.class, "localStrategy", MdsalRestconfStrategy.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final @NonNull ImmutableMap<QName, RpcImplementation> localRpcs;
    private final @NonNull DOMMountPointService mountPointService;
    private final @NonNull MdsalDatabindProvider databindProvider;
    private final @NonNull DOMDataBroker dataBroker;
    private final @Nullable DOMRpcService rpcService;
    private final @Nullable DOMActionService actionService;
    private final @NonNull QueryParams emptyQueryParams;

    @SuppressFBWarnings(value = "URF_UNREAD_FIELD", justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    private volatile MdsalRestconfStrategy localStrategy;

    public MdsalRestconfServer(final MdsalDatabindProvider databindProvider, final DOMDataBroker dataBroker,
            final DOMRpcService rpcService, final DOMActionService actionService,
            final DOMMountPointService mountPointService, final List<RpcImplementation> localRpcs,
            final PrettyPrintParam prettyPrint) {
        this.databindProvider = requireNonNull(databindProvider);
        this.dataBroker = requireNonNull(dataBroker);
        this.rpcService = requireNonNull(rpcService);
        this.actionService = requireNonNull(actionService);
        this.mountPointService = requireNonNull(mountPointService);
        emptyQueryParams = new QueryParams(QueryParameters.of(), prettyPrint);

        this.localRpcs = Maps.uniqueIndex(localRpcs, RpcImplementation::qname);
        localStrategy = createLocalStrategy(databindProvider.currentDatabind());
    }

    @Inject
    public MdsalRestconfServer(final MdsalDatabindProvider databindProvider, final DOMDataBroker dataBroker,
            final DOMRpcService rpcService, final DOMActionService actionService,
            final DOMMountPointService mountPointService, final List<RpcImplementation> localRpcs) {
        this(databindProvider, dataBroker, rpcService, actionService, mountPointService, localRpcs,
            PrettyPrintParam.FALSE);
    }

    @Activate
    public MdsalRestconfServer(@Reference final MdsalDatabindProvider databindProvider,
            @Reference final DOMDataBroker dataBroker, @Reference final DOMRpcService rpcService,
            @Reference final DOMActionService actionService,
            @Reference final DOMMountPointService mountPointService,
            @Reference(policyOption = ReferencePolicyOption.GREEDY) final List<RpcImplementation> localRpcs,
            // FIXME: dynamic at some point
            final RestconfServerConfiguration configuration) {
        this(databindProvider, dataBroker, rpcService, actionService, mountPointService, localRpcs,
            PrettyPrintParam.of(configuration.pretty$_$print()));
    }

    public MdsalRestconfServer(final MdsalDatabindProvider databindProvider, final DOMDataBroker dataBroker,
            final DOMRpcService rpcService, final DOMActionService actionService,
            final DOMMountPointService mountPointService, final PrettyPrintParam prettyPrint,
            final RpcImplementation... localRpcs) {
        this(databindProvider, dataBroker, rpcService, actionService, mountPointService, List.of(localRpcs),
            prettyPrint);
    }

    public MdsalRestconfServer(final MdsalDatabindProvider databindProvider, final DOMDataBroker dataBroker,
            final DOMRpcService rpcService, final DOMActionService actionService,
            final DOMMountPointService mountPointService, final RpcImplementation... localRpcs) {
        this(databindProvider, dataBroker, rpcService, actionService, mountPointService, PrettyPrintParam.FALSE,
            localRpcs);
    }

    private @NonNull MdsalRestconfStrategy createLocalStrategy(final DatabindContext databind) {
        return new MdsalRestconfStrategy(databind, dataBroker, localRpcs, rpcService, actionService,
            databindProvider.sourceProvider(), mountPointService);
    }

    private @NonNull MdsalRestconfStrategy localStrategy() {
        final var strategy = verifyNotNull((@NonNull MdsalRestconfStrategy) LOCAL_STRATEGY.getAcquire(this));
        final var databind = databindProvider.currentDatabind();
        return databind.equals(strategy.databind()) ? strategy : updateLocalStrategy(databind);
    }

    private @NonNull MdsalRestconfStrategy updateLocalStrategy(final DatabindContext databind) {
        final var strategy = createLocalStrategy(databind);
        localStrategy = strategy;
        return strategy;
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        localStrategy = null;
    }

    private @NonNull QueryParams queryParams(final @NonNull QueryParameters params) {
        return params.isEmpty() ? emptyQueryParams : new QueryParams(params, emptyQueryParams.prettyPrint());
    }

    @Override
    public RestconfFuture<Empty> dataDELETE(final ApiPath identifier) {
        final StrategyAndTail stratAndTail;
        try {
            stratAndTail = localStrategy().resolveStrategy(identifier);
        } catch (RestconfDocumentedException e) {
            return RestconfFuture.failed(e);
        }
        return stratAndTail.strategy().dataDELETE(stratAndTail.tail());
    }

    @Override
    public RestconfFuture<DataGetResult> dataGET(final QueryParameters params) {
        return localStrategy().dataGET(ApiPath.empty(), queryParams(params));
    }

    @Override
    public RestconfFuture<DataGetResult> dataGET(final ApiPath identifier, final QueryParameters params) {
        final StrategyAndTail stratAndTail;
        try {
            stratAndTail = localStrategy().resolveStrategy(identifier);
        } catch (RestconfDocumentedException e) {
            return RestconfFuture.failed(e);
        }
        return stratAndTail.strategy().dataGET(stratAndTail.tail(), queryParams(params));
    }

    @Override
    public RestconfFuture<DataPatchResult> dataPATCH(final ResourceBody body) {
        return localStrategy().dataPATCH(ApiPath.empty(), body);
    }

    @Override
    public RestconfFuture<DataPatchResult> dataPATCH(final ApiPath identifier, final ResourceBody body) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(identifier);
        } catch (RestconfDocumentedException e) {
            return RestconfFuture.failed(e);
        }
        return strategyAndTail.strategy().dataPATCH(strategyAndTail.tail(), body);
    }

    @Override
    public RestconfFuture<DataYangPatchResult> dataPATCH(final QueryParameters params, final PatchBody body) {
        return localStrategy().dataPATCH(ApiPath.empty(), queryParams(params), body);
    }

    @Override
    public RestconfFuture<DataYangPatchResult> dataPATCH(final ApiPath identifier, final QueryParameters params,
            final PatchBody body) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(identifier);
        } catch (RestconfDocumentedException e) {
            return RestconfFuture.failed(e);
        }
        return strategyAndTail.strategy().dataPATCH(strategyAndTail.tail(), queryParams(params), body);
    }

    @Override
    public RestconfFuture<CreateResourceResult> dataPOST(final QueryParameters params, final ChildBody body) {
        return localStrategy().dataCreatePOST(queryParams(params), body);
    }

    @Override
    public RestconfFuture<? extends DataPostResult> dataPOST(final ApiPath identifier, final QueryParameters params,
            final DataPostBody body) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(identifier);
        } catch (RestconfDocumentedException e) {
            return RestconfFuture.failed(e);
        }
        return strategyAndTail.strategy().dataPOST(strategyAndTail.tail(), queryParams(params), body);
    }

    @Override
    public RestconfFuture<DataPutResult> dataPUT(final QueryParameters params, final ResourceBody body) {
        return localStrategy().dataPUT(ApiPath.empty(), queryParams(params), body);
    }

    @Override
    public RestconfFuture<DataPutResult> dataPUT(final ApiPath identifier, final QueryParameters params,
            final ResourceBody body) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(identifier);
        } catch (RestconfDocumentedException e) {
            return RestconfFuture.failed(e);
        }
        return strategyAndTail.strategy().dataPUT(strategyAndTail.tail(), queryParams(params), body);
    }

    @Override
    public RestconfFuture<ModulesGetResult> modulesYangGET(final String fileName, final String revision) {
        return modulesGET(fileName, revision, YangTextSource.class);
    }

    @Override
    public RestconfFuture<ModulesGetResult> modulesYangGET(final ApiPath mountPath, final String fileName,
            final String revision) {
        return modulesGET(mountPath, fileName, revision, YangTextSource.class);
    }

    @Override
    public RestconfFuture<ModulesGetResult> modulesYinGET(final String fileName, final String revision) {
        return modulesGET(fileName, revision, YinTextSource.class);
    }

    @Override
    public RestconfFuture<ModulesGetResult> modulesYinGET(final ApiPath mountPath, final String fileName,
            final String revision) {
        return modulesGET(mountPath, fileName, revision, YinTextSource.class);
    }

    private @NonNull RestconfFuture<ModulesGetResult> modulesGET(final String fileName, final String revision,
            final Class<? extends SourceRepresentation> representation) {
        return modulesGET(localStrategy(), fileName, revision, representation);
    }

    private @NonNull RestconfFuture<ModulesGetResult> modulesGET(final ApiPath mountPath, final String fileName,
            final String revision, final Class<? extends SourceRepresentation> representation) {
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
            final Class<? extends SourceRepresentation> representation) {
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
    public RestconfFuture<FormattableBody> operationsGET() {
        return localStrategy().operationsGET(emptyQueryParams);
    }

    @Override
    public RestconfFuture<FormattableBody> operationsGET(final ApiPath operation) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(operation);
        } catch (RestconfDocumentedException e) {
            return RestconfFuture.failed(e);
        }

        final var strategy = strategyAndTail.strategy();
        final var tail = strategyAndTail.tail();
        return tail.isEmpty() ? strategy.operationsGET(emptyQueryParams)
            : strategy.operationsGET(tail, emptyQueryParams);
    }

    @Override
    public RestconfFuture<InvokeResult> operationsPOST(final URI restconfURI, final ApiPath apiPath,
            final QueryParameters params, final OperationInputBody body) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(apiPath);
        } catch (RestconfDocumentedException e) {
            return RestconfFuture.failed(e);
        }
        final var strategy = strategyAndTail.strategy();
        return strategy.operationsPOST(restconfURI, strategyAndTail.tail(), queryParams(params), body);
    }

    @Override
    public RestconfFuture<FormattableBody> yangLibraryVersionGET() {
        return localStrategy().yangLibraryVersionGET(emptyQueryParams);
    }
}
