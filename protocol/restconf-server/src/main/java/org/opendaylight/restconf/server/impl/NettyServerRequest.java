/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.AbstractServerRequest;
import org.opendaylight.restconf.server.api.ServerRequest;

/**
 * A {@link ServerRequest} originating in this implementation.
 *
 * @param T type of reported result
 */
@NonNullByDefault
abstract class NettyServerRequest<T> extends AbstractServerRequest<T> {
    NettyServerRequest(final QueryParameters queryParameters, final PrettyPrintParam defaultPrettyPrint) {
        super(queryParameters, defaultPrettyPrint);
    }
}