/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.server.api.ServerRequest;

@NonNullByDefault
public record FailedHttpGetResource(RequestException cause) implements HttpGetResource {
    public FailedHttpGetResource {
        requireNonNull(cause);
    }

    @Override
    public void httpGET(final ServerRequest<FormattableBody> request) {
        request.failWith(cause);
    }

    @Override
    public void httpGET(final ServerRequest<FormattableBody> request, final ApiPath apiPath) {
        throw new UnsupportedOperationException();
    }
}
