/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.ModulesGetResult;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.SourceRepresentation;

/**
 * A {@link ServerRpcOperations} implementation fails all requests with {@link ErrorTag#OPERATION_NOT_SUPPORTED}..
 */
@NonNullByDefault
public final class NotSupportedServerModulesOperations implements ServerModulesOperations {
    public static final NotSupportedServerModulesOperations INSTANCE = new NotSupportedServerModulesOperations();

    private NotSupportedServerModulesOperations() {
        // Hidden on purpose
    }

    @Override
    public void getModelSource(final ServerRequest<ModulesGetResult> request, final SourceIdentifier source,
            final Class<? extends SourceRepresentation> representation) {
        request.failWith(new RequestException(ErrorType.PROTOCOL, ErrorTag.OPERATION_NOT_SUPPORTED,
            "Modules not supported"));
    }
}
