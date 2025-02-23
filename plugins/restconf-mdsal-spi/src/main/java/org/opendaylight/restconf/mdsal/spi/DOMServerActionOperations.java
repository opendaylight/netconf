/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.netconf.common.DatabindPath.Action;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.ServerActionOperations;
import org.opendaylight.restconf.server.spi.ServerRpcOperations;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * A {@link ServerRpcOperations} delegating to a {@link DOMActionService}.
 */
public record DOMServerActionOperations(DOMActionService actionService) implements ServerActionOperations {
    public DOMServerActionOperations {
        requireNonNull(actionService);
    }

    @Override
    public void invokeAction(final ServerRequest<? super InvokeResult> request, final Action path,
            final ContainerNode input) {
        Futures.addCallback(actionService.invokeAction(
            path.inference().toSchemaInferenceStack().toSchemaNodeIdentifier(),
            DOMDataTreeIdentifier.of(LogicalDatastoreType.OPERATIONAL, path.instance()), input),
            new DOMRpcResultCallback(request, path), MoreExecutors.directExecutor());
    }
}
