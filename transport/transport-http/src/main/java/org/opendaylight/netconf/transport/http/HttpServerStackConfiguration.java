/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
import org.opendaylight.yangtools.binding.DataContainer;

/**
 * Placeholder {@link HttpServerStackGrouping} implementation for use as long as we do not have a YANG manifestation
 * of our configuration.
 *
 * @param getTransport wrapped {@link Transport} instance
 */
@Beta
@NonNullByDefault
public record HttpServerStackConfiguration(Transport getTransport) implements HttpServerStackGrouping {
    public HttpServerStackConfiguration {
        requireNonNull(getTransport);
    }

    @Override
    public Class<? extends DataContainer> implementedInterface() {
        // Should never be called
        throw new UnsupportedOperationException();
    }
}
