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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.MdsalRestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.restconf.server.RpcImplementation;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RESTCONF server implemented on top of MD-SAL.
 */
// FIXME: factor out the 'RestconfServer' interface once we're ready
// FIXME: this should live in 'org.opendaylight.restconf.server.mdsal' package
@Singleton
@Component(service = MdsalRestconfServer.class)
public final class MdsalRestconfServer {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalRestconfServer.class);
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
    private final @NonNull DOMDataBroker dataBroker;
    private final @Nullable DOMRpcService rpcService;

    @SuppressWarnings("unused")
    private volatile RestconfStrategy localStrategy;

    @Inject
    @Activate
    public MdsalRestconfServer(@Reference final DOMDataBroker dataBroker, @Reference final DOMRpcService rpcService,
            @Reference final DOMMountPointService mountPointService,
            @Reference final List<RpcImplementation> localRpcs) {
        this.dataBroker = requireNonNull(dataBroker);
        this.rpcService = requireNonNull(rpcService);
        this.mountPointService = requireNonNull(mountPointService);
        this.localRpcs = Maps.uniqueIndex(localRpcs, RpcImplementation::qname);
    }

    public MdsalRestconfServer(final DOMDataBroker dataBroker, final DOMRpcService rpcService,
            final DOMMountPointService mountPointService, final RpcImplementation... localRpcs) {
        this(dataBroker, rpcService, mountPointService, List.of(localRpcs));
    }

    @NonNull InstanceIdentifierContext bindRequestPath(final DatabindContext databind, final String identifier) {
        // FIXME: go through ApiPath first. That part should eventually live in callers
        // FIXME: DatabindContext looks like it should be internal
        return verifyNotNull(ParserIdentifier.toInstanceIdentifier(requireNonNull(identifier), databind.modelContext(),
            mountPointService));
    }

    @SuppressWarnings("static-method")
    @NonNull InstanceIdentifierContext bindRequestRoot(final DatabindContext databind) {
        return InstanceIdentifierContext.ofLocalRoot(databind.modelContext());
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
