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
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.DefaultRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.DefaultRestconfStrategy.StrategyAndTail;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
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
    public void dataDELETE(final ServerRequest<Empty> request, final ApiPath identifier) {
        final StrategyAndTail stratAndTail;
        try {
            stratAndTail = localStrategy().resolveStrategy(identifier);
        } catch (ServerException e) {
            request.completeWith(e);
            return;
        }
        stratAndTail.strategy().dataDELETE(request, stratAndTail.tail());
    }

    @Override
    public void dataGET(final ServerRequest<DataGetResult> request) {
        localStrategy().dataGET(request, ApiPath.empty());
    }

    @Override
    public void dataGET(final ServerRequest<DataGetResult> request, final ApiPath identifier) {
        final StrategyAndTail stratAndTail;
        try {
            stratAndTail = localStrategy().resolveStrategy(identifier);
        } catch (ServerException e) {
            request.completeWith(e);
            return;
        }
        stratAndTail.strategy().dataGET(request, stratAndTail.tail());
    }

    @Override
    public void dataPATCH(final ServerRequest<DataPatchResult> request, final ResourceBody body) {
        localStrategy().dataPATCH(request, ApiPath.empty(), body);
    }

    @Override
    public void dataPATCH(final ServerRequest<DataPatchResult> request, final ApiPath identifier,
            final ResourceBody body) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(identifier);
        } catch (ServerException e) {
            request.completeWith(e);
            return;
        }
        strategyAndTail.strategy().dataPATCH(request, strategyAndTail.tail(), body);
    }

    @Override
    public void dataPATCH(final ServerRequest<DataYangPatchResult> request, final PatchBody body) {
        localStrategy().dataPATCH(request, ApiPath.empty(), body);
    }

    @Override
    public void dataPATCH(final ServerRequest<DataYangPatchResult> request, final ApiPath identifier,
            final PatchBody body) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(identifier);
        } catch (ServerException e) {
            request.completeWith(e);
            return;
        }
        strategyAndTail.strategy().dataPATCH(request, strategyAndTail.tail(), body);
    }

    @Override
    public void dataPOST(final ServerRequest<CreateResourceResult> request, final ChildBody body) {
        localStrategy().dataCreatePOST(request, body);
    }

    @Override
    public void dataPOST(final ServerRequest<DataPostResult> request, final ApiPath identifier,
            final DataPostBody body) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(identifier);
        } catch (ServerException e) {
            request.completeWith(e);
            return;
        }
        strategyAndTail.strategy().dataPOST(request, strategyAndTail.tail(), body);
    }

    @Override
    public void dataPUT(final ServerRequest<DataPutResult> request, final ResourceBody body) {
        localStrategy().dataPUT(request, ApiPath.empty(), body);
    }

    @Override
    public void dataPUT(final ServerRequest<DataPutResult> request, final ApiPath identifier, final ResourceBody body) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(identifier);
        } catch (ServerException e) {
            request.completeWith(e);
            return ;
        }
        strategyAndTail.strategy().dataPUT(request, strategyAndTail.tail(), body);
    }

    @Override
    public void modulesYangGET(final ServerRequest<ModulesGetResult> request, final String fileName,
            final String revision) {
        modulesGET(request, fileName, revision, YangTextSource.class);
    }

    @Override
    public void modulesYangGET(final ServerRequest<ModulesGetResult> request, final ApiPath mountPath,
            final String fileName, final String revision) {
        modulesGET(request, mountPath, fileName, revision, YangTextSource.class);
    }

    @Override
    public void modulesYinGET(final ServerRequest<ModulesGetResult> request, final String fileName,
            final String revision) {
        modulesGET(request, fileName, revision, YinTextSource.class);
    }

    @Override
    public void modulesYinGET(final ServerRequest<ModulesGetResult> request, final ApiPath mountPath,
            final String fileName, final String revision) {
        modulesGET(request, mountPath, fileName, revision, YinTextSource.class);
    }

    private void modulesGET(final ServerRequest<ModulesGetResult> request, final String fileName, final String revision,
            final Class<? extends SourceRepresentation> representation) {
        modulesGET(request, localStrategy(), fileName, revision, representation);
    }

    private void modulesGET(final ServerRequest<ModulesGetResult> request, final ApiPath mountPath,
            final String fileName, final String revision, final Class<? extends SourceRepresentation> representation) {
        final var mountOffset = mountPath.indexOf("yang-ext", "mount");
        if (mountOffset != mountPath.steps().size() - 1) {
            request.completeWith(new ServerException("Mount path has to end with yang-ext:mount"));
            return;
        }

        final StrategyAndTail stratAndTail;
        try {
            stratAndTail = localStrategy().resolveStrategy(mountPath);
        } catch (ServerException e) {
            request.completeWith(e);
            return;
        }
        // FIXME: require remnant to be empty
        modulesGET(request, stratAndTail.strategy(), fileName, revision, representation);
    }

    private static void modulesGET(final ServerRequest<ModulesGetResult> request,
            final DefaultRestconfStrategy strategy, final String moduleName, final String revisionStr,
            final Class<? extends SourceRepresentation> representation) {
        if (moduleName == null) {
            request.completeWith(new ServerException(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                "Module name must be supplied"));
            return;
        }
        if (moduleName.isEmpty() || !YangNames.IDENTIFIER_START.matches(moduleName.charAt(0))) {
            request.completeWith(new ServerException(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                "Identifier must start with character from set 'a-zA-Z_"));
            return;
        }
        if (moduleName.toUpperCase(Locale.ROOT).startsWith("XML")) {
            request.completeWith(new ServerException(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                "Identifier must NOT start with XML ignore case"));
            return;
        }
        if (YangNames.NOT_IDENTIFIER_PART.matchesAnyOf(moduleName.substring(1))) {
            request.completeWith(new ServerException(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                "Supplied name has not expected identifier format"));
            return;
        }

        // YANG Revision-compliant string is required
        final Revision revision;
        try {
            revision = Revision.ofNullable(revisionStr).orElse(null);
        } catch (final DateTimeParseException e) {
            request.completeWith(new ServerException(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                "Supplied revision is not in expected date format YYYY-mm-dd", e));
            return;
        }

        strategy.resolveSource(request, new SourceIdentifier(moduleName, revision), representation);
    }

    @Override
    public void operationsGET(final ServerRequest<FormattableBody> request) {
        localStrategy().operationsGET(request);
    }

    @Override
    public void operationsGET(final ServerRequest<FormattableBody> request, final ApiPath operation) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(operation);
        } catch (ServerException e) {
            request.completeWith(e);
            return;
        }

        final var strategy = strategyAndTail.strategy();
        final var tail = strategyAndTail.tail();
        if (tail.isEmpty()) {
            strategy.operationsGET(request);
        } else {
            strategy.operationsGET(request, tail);
        }
    }

    @Override
    public void operationsPOST(final ServerRequest<InvokeResult> request, final URI restconfURI, final ApiPath apiPath,
            final OperationInputBody body) {
        final StrategyAndTail strategyAndTail;
        try {
            strategyAndTail = localStrategy().resolveStrategy(apiPath);
        } catch (ServerException e) {
            request.completeWith(e);
            return;
        }
        strategyAndTail.strategy().operationsPOST(request, restconfURI, strategyAndTail.tail(), body);
    }

    @Override
    public void yangLibraryVersionGET(final ServerRequest<FormattableBody> request) {
        localStrategy().yangLibraryVersionGET(request);
    }
}
