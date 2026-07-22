/*
 * Copyright (c) 2026 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it;

import static java.util.Objects.requireNonNull;

import org.opendaylight.netconf.transport.http.HTTPScheme;

/**
 * The HTTP protocol versions which we support, paired with the {@link HTTPScheme} used.
 */
public enum ProtocolVersion {
    HTTP_1_1(HTTPScheme.HTTP),
    HTTP_2(HTTPScheme.HTTP),
    HTTP_3(HTTPScheme.HTTPS);

    private final HTTPScheme scheme;

    ProtocolVersion(final HTTPScheme scheme) {
        this.scheme = requireNonNull(scheme);
    }

    public HTTPScheme scheme() {
        return scheme;
    }
}
