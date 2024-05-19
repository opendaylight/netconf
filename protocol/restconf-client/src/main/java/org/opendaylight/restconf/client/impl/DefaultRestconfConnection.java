/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.client.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.mdsal.binding.runtime.api.BindingRuntimeContext;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.client.RestconfConnection;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.RestconfState;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

@NonNullByDefault
final class DefaultRestconfConnection implements RestconfConnection {
    private final BindingRuntimeContext runtimeContext;

    DefaultRestconfConnection(final BindingRuntimeContext runtimeContext) {
        this.runtimeContext = requireNonNull(runtimeContext);
    }

    @Override
    public ListenableFuture<RestconfState> restconfState() {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }

    @Override
    public ListenableFuture<ContainerNode> get(final ContentParam content) {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }

    @Override
    public ListenableFuture<NormalizedNode> get(final ContentParam content, final YangInstanceIdentifier path) {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }
}
