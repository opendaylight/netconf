/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.impl;

import org.opendaylight.restconf.server.api.RestconfServer;

/**
 * Request processor.
 */
interface RequestProcessor {

    /**
     * Indicates if current processor is suitable for request.
     *
     * @param context request context
     * @return true if current processor can process the request, false otherwise
     */
    boolean matches(RequestContext context);

    /**
     * Performs processing of the request.
     *
     * @param restconfService service instance
     * @param context request context
     */
    void process(RestconfServer restconfService, RequestContext context);
}
