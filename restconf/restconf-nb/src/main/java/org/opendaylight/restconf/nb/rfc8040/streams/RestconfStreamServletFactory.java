/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import javax.servlet.http.HttpServlet;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.server.spi.RestconfStream;

/**
 * A helper for creating {@link HttpServlet}s which provide bridge between JAX-RS and {@link RestconfStream.Registry}.
 *
 * @deprecated This interface exists only to support SSE/Websocket delivery. It will be removed when support for
 *             WebSockets is removed.
 */
@Deprecated(since = "7.0.0", forRemoval = true)
public interface RestconfStreamServletFactory {
    /**
     * Return the value of {@code {+restconf}} macro. May be empty, guaranteed to not end with {@code /}.
     *
     * @return the value of {@code {+restconf}} macro
     */
    @NonNull String restconf();

    @NonNull HttpServlet newStreamServlet();
}
