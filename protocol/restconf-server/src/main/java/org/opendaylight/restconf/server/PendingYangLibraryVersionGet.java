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
import org.opendaylight.restconf.api.FormattableBody;

/**
 * A GET or HEAD request to the /yang-library-version resource.
 */
@NonNullByDefault
final class PendingYangLibraryVersionGet extends AbstractDataPendingGet {
    PendingYangLibraryVersionGet(final EndpointInvariants invariants, final URI targetUri,
            final MessageEncoding encoding, final boolean withContent) {
        super(invariants, targetUri, encoding, withContent);
    }

    @Override
    void execute(final NettyServerRequest<FormattableBody> request, final InputStream body) {
        server().yangLibraryVersionGET(request);
    }
}
