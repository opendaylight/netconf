/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.MessageEncoding;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;

/**
 * Invariant setup of a particular endpoint. This DTO exists only to make method signatures less verbose.
 *
 * @param restconfPath {@code /{+restconf}/}, i.e. an absolute path conforming to {@link RestconfServer}'s
 *                     {@code restconfURI} contract
 */
@NonNullByDefault
public record EndpointInvariants(
        RestconfServer server,
        PrettyPrintParam defaultPrettyPrint,
        ErrorTagMapping errorTagMapping,
        MessageEncoding defaultEncoding,
        URI restconfPath) {
    public EndpointInvariants {
        requireNonNull(server);
        requireNonNull(defaultPrettyPrint);
        requireNonNull(errorTagMapping);
        requireNonNull(defaultEncoding);
        requireNonNull(restconfPath);
    }
}
