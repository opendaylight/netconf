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
import org.opendaylight.restconf.server.api.OptionsResult;
import org.opendaylight.restconf.server.api.PatchBody;
import org.opendaylight.restconf.server.api.ResourceBody;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
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

/**
 * A RESTCONF server implemented on top of MD-SAL.
 */
@Singleton
@Component(service = RestconfServer.class)
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

    @SuppressFBWarnings(value = "URF_UNREAD_FIELD", justification = "https://github.com/spotbugs/spotbugs/issues/2749")
    private volatile MdsalRestconfStrategy localStrategy;

    @Inject
    @Activate
    public MdsalRestconfServer(@Reference final MdsalDatabindProvider databindProvider,
            @Reference final DOMDataBroker dataBroker, @Reference final DOMRpcService rpcService,
            @Reference final DOMActionService actionService,
            @Reference final DOMMountPointService mountPointService,
            @Reference(policyOption = ReferencePolicyOption.GREEDY) final List<RpcImplementation> localRpcs) {
        this.databindProvider = requireNonNull(databindProvider);
        this.dataBroker = requireNonNull(dataBroker);
        this.rpcService = requireNonNull(rpcService);
        this.actionService = requireNonNull(actionService);
        this.mountPointService = requireNonNull(mountPointService);

        this.localRpcs = Maps.uniqueIndex(localRpcs, RpcImplementation::qname);
        localStrategy = createLocalStrategy(databindProvider.currentDatabind());
    }

    public MdsalRestconfServer(final MdsalDatabindProvider databindProvider, final DOMDataBroker dataBroker,
            final DOMRpcService rpcService, final DOMActionService actionService,
            final DOMMountPointService mountPointService, final RpcImplementation... localRpcs) {
        this(databindProvider, dataBroker, rpcService, actionService, mountPointService, List.of(localRpcs));
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

    @Override
    public RestconfFuture<Empty> dataDELETE(final ServerRequest request, final ApiPath identifier) {
        final StrategyAndTail stratAndTail;
        try {
            stratAndTail = localStrategy().resolveStrategy(identifier);
        } catch (ServerException e) {
            return RestconfFuture.failed(e.toLegacy());
        }
        return stratAndTail.strategy().dataDELETE(request, stratAndTail.tail());
    }

    @Override
    public RestconfFuture<DataGetResult> dataGET(final ServerRequest request) {
        return localStrategy().dataGET(request, ApiPath.empty());
    }

    @Override
    public RestconfFuture<DataGetResult> dataGET(final ServerRequest request, final ApiPath identifier) {
        final StrategyAndTail stratAndTail;
        try {
            stratAndTail = localStrategy().resolveStrategy(identifier);
        } catch (ServerException e) {
            return RestconfFuture.failed(e.toLegacy());
        }
        return stratAndTail.strategy().dataGET(request, stratAndTail.tail());
    }

    @Override
    public RestconfFuture<OptionsResult> dataOPTIONS(final ServerRequest request) {
        return localStrategy().dataOPTIONS(request);
    }

    @Override
    public RestconfFuture<OptionsResult> dataOPTIONS(final ServerRequest request, final ApiPath identifier) {
        final StrategyAndTail stratAndTail;
        try {
            stratAndTail = localStrategy().resolveStrategy(identifier);
        } catch (ServerException e) {
            return RestconfFuture.failed(e.toLegacy());
        }
        final var strategy = stratAndTail.strategy();
        final var tail = stratAndTail.tail();
        return tail.isEmpty() ? strategy.dataOPTIONS(request) : strategy.dataOPTIONS(request, tail);
    }

    @Override
    public RestconfFuture<DataPatchResult> dataPATCH(final ServerRequest request, final ResourceBody body) {
        return localStrategy().dataPATCH(ApiPath.empty(), body);
    }

    @Override
    public RestconfFuture<DataPatchResult> dataPATCH(final ServerRequest request, final ApiPath identifier,
            final ResourceBody body) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(identifier);
        } catch (ServerException e) {
            return RestconfFuture.failed(e.toLegacy());
        }
        return strategyAndTail.strategy().dataPATCH(strategyAndTail.tail(), body);
    }

    @Override
    public RestconfFuture<DataYangPatchResult> dataPATCH(final ServerRequest request, final PatchBody body) {
        return localStrategy().dataPATCH(ApiPath.empty(), body);
    }

    @Override
    public RestconfFuture<DataYangPatchResult> dataPATCH(final ServerRequest request, final ApiPath identifier,
            final PatchBody body) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(identifier);
        } catch (ServerException e) {
            return RestconfFuture.failed(e.toLegacy());
        }
        return strategyAndTail.strategy().dataPATCH(strategyAndTail.tail(), body);
    }

    @Override
    public RestconfFuture<CreateResourceResult> dataPOST(final ServerRequest request, final ChildBody body) {
        return localStrategy().dataCreatePOST(request, body);
    }

    @Override
    public RestconfFuture<? extends DataPostResult> dataPOST(final ServerRequest request, final ApiPath identifier,
            final DataPostBody body) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(identifier);
        } catch (ServerException e) {
            return RestconfFuture.failed(e.toLegacy());
        }
        return strategyAndTail.strategy().dataPOST(request, strategyAndTail.tail(), body);
    }

    @Override
    public RestconfFuture<DataPutResult> dataPUT(final ServerRequest request, final ResourceBody body) {
        return localStrategy().dataPUT(request, ApiPath.empty(), body);
    }

    @Override
    public RestconfFuture<DataPutResult> dataPUT(final ServerRequest request, final ApiPath identifier,
            final ResourceBody body) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(identifier);
        } catch (ServerException e) {
            return RestconfFuture.failed(e.toLegacy());
        }
        return strategyAndTail.strategy().dataPUT(request, strategyAndTail.tail(), body);
    }

    @Override
    public RestconfFuture<ModulesGetResult> modulesYangGET(final ServerRequest request, final String fileName,
            final String revision) {
        return modulesGET(fileName, revision, YangTextSource.class);
    }

    @Override
    public RestconfFuture<ModulesGetResult> modulesYangGET(final ServerRequest request, final ApiPath mountPath,
            final String fileName, final String revision) {
        return modulesGET(mountPath, fileName, revision, YangTextSource.class);
    }

    @Override
    public RestconfFuture<ModulesGetResult> modulesYinGET(final ServerRequest request, final String fileName,
            final String revision) {
        return modulesGET(fileName, revision, YinTextSource.class);
    }

    @Override
    public RestconfFuture<ModulesGetResult> modulesYinGET(final ServerRequest request, final ApiPath mountPath,
            final String fileName, final String revision) {
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
        } catch (ServerException e) {
            return RestconfFuture.failed(e.toLegacy());
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
    public RestconfFuture<FormattableBody> operationsGET(final ServerRequest request) {
        return localStrategy().operationsGET(request);
    }

    @Override
    public RestconfFuture<FormattableBody> operationsGET(final ServerRequest request, final ApiPath operation) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(operation);
        } catch (ServerException e) {
            return RestconfFuture.failed(e.toLegacy());
        }

        final var strategy = strategyAndTail.strategy();
        final var tail = strategyAndTail.tail();
        return tail.isEmpty() ? strategy.operationsGET(request) : strategy.operationsGET(request, tail);
    }

    @Override
    public RestconfFuture<OptionsResult> operationsOPTIONS(final ServerRequest request, final ApiPath operation) {
        final StrategyAndTail strategyAndPath;
        try {
            strategyAndPath = localStrategy().resolveStrategy(operation);
        } catch (ServerException e) {
            return RestconfFuture.failed(e.toLegacy());
        }
        final var tail = strategyAndPath.tail();
        return tail.isEmpty() ? RestconfFuture.of(OptionsResult.READ_ONLY)
            : strategyAndPath.strategy().operationsOPTIONS(request, tail);
    }

    @Override
    public RestconfFuture<InvokeResult> operationsPOST(final ServerRequest request, final URI restconfURI,
            final ApiPath apiPath, final OperationInputBody body) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(apiPath);
        } catch (ServerException e) {
            return RestconfFuture.failed(e.toLegacy());
        }
        final var strategy = strategyAndTail.strategy();
        return strategy.operationsPOST(request, restconfURI, strategyAndTail.tail(), body);
    }

    @Override
    public RestconfFuture<FormattableBody> yangLibraryVersionGET(final ServerRequest request) {
        return localStrategy().yangLibraryVersionGET(request);
    }
}
