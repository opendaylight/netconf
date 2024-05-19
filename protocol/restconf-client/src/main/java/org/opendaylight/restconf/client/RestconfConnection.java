/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.client;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.RestconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.Streams;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * A RESTCONF client session. It has a connection established to a remote RESTCONF server.
 */
@NonNullByDefault
public interface RestconfConnection {

    default ListenableFuture<Capabilities> getCapabilities() {
        return Futures.transform(restconfState(), RestconfState::nonnullCapabilities, MoreExecutors.directExecutor());
    }

    default ListenableFuture<Streams> getStreams() {
        return Futures.transform(restconfState(), RestconfState::nonnullStreams, MoreExecutors.directExecutor());
    }

    // FIXME: integrated error reporting with a dedicated future (or callback)
    ListenableFuture<RestconfState> restconfState();

    ListenableFuture<ContainerNode> get(ContentParam content);

    ListenableFuture<NormalizedNode> get(ContentParam content, YangInstanceIdentifier path);
}
