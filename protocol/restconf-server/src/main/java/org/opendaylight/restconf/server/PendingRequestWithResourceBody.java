/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import java.io.InputStream;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ConsumableBody;
import org.opendaylight.restconf.server.api.JsonResourceBody;
import org.opendaylight.restconf.server.api.ResourceBody;
import org.opendaylight.restconf.server.api.XmlResourceBody;

/**
 * A {@link PendingRequestWithResourceBody} with a {@link ConsumableBody}. This class communicates takes care
 * of wrapping the incoming {@link InputStream} body with the corresponding {@link ConsumableBody} and ensures it gets
 * deallocated when no longer needed.
 *
 * @param <T> server response type
 */
@NonNullByDefault
abstract class PendingRequestWithResourceBody<T> extends PendingRequestWithBody<T, ResourceBody> {
    PendingRequestWithResourceBody(final EndpointInvariants invariants, final URI targetUri,
            final MessageEncoding encoding) {
        super(invariants, targetUri, encoding);
    }

    @Override
    final ResourceBody wrapBody(final InputStream body) {
        return switch (encoding) {
            case JSON -> new JsonResourceBody(body);
            case XML -> new XmlResourceBody(body);
        };
    }
}
