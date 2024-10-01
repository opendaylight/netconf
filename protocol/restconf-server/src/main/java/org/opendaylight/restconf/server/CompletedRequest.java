/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A {@link PreparedRequest} which is already complete, this qualifying to be a {@link Response}.
 */
@NonNullByDefault
non-sealed interface CompletedRequest extends PreparedRequest, Response {

    FullHttpResponse toHttpResponse(final HttpVersion version);
}
