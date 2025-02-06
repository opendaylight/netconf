/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import com.google.common.annotations.Beta;

/**
 * The result of
 * {@link HTTPServerSession#prepareRequest(ImplementedMethod, java.net.URI, io.netty.handler.codec.http.HttpHeaders)}.
 * This can either be a {@link CompletedRequest} or a {@link PendingRequest}.
 */
@Beta
public sealed interface PreparedRequest permits CompletedRequest, PendingRequest {
    // Nothing else
}
