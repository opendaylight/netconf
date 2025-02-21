/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteOperations;
import org.opendaylight.restconf.server.spi.RestconfStream;

/**
 * Common interface for encapsulation of the two places we export our defined streams.
 */
@NonNullByDefault
abstract sealed class StreamSupport permits Rfc8040StreamSupport, Rfc8639StreamSupport {

    abstract void putStream(DOMDataTreeWriteOperations transaction, RestconfStream<?> stream, String description,
        @Nullable URI restconfURI);

    abstract void deleteStream(DOMDataTreeWriteOperations transaction, String streamName);
}
