/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.DatabindPath.Action;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * A {@link ServerRpcOperations} implementation fails all {@link #invokeAction(ServerRequest, Action, ContainerNode)}
 * requests with {@link ErrorTag#OPERATION_NOT_SUPPORTED}.
 */
@NonNullByDefault
public final class NotSupportedServerActionOperations implements ServerActionOperations {
    public static final NotSupportedServerActionOperations INSTANCE = new NotSupportedServerActionOperations();

    private NotSupportedServerActionOperations() {
        // Hidden on purpose
    }

    @Override
    public void invokeAction(final ServerRequest<? super InvokeResult> request, final Action path,
            final ContainerNode input) {
        request.failWith(new RequestException(ErrorType.PROTOCOL, ErrorTag.OPERATION_NOT_SUPPORTED,
            "Action not supported"));
    }
}
