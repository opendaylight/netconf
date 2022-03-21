/*
 * Copyright (c) 2017 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.spi.AbstractIdentifierAwareJaxRsProvider;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;

/**
 * Common superclass for readers producing {@link NormalizedNodePayload}.
 */
abstract class AbstractNormalizedNodeBodyReader extends AbstractIdentifierAwareJaxRsProvider<NormalizedNodePayload> {
    AbstractNormalizedNodeBodyReader(final SchemaContextHandler schemaContextHandler,
            final DOMMountPointService mountPointService) {
        super(schemaContextHandler, mountPointService);
    }

    public final void injectParams(final UriInfo uriInfo, final Request request) {
        setUriInfo(uriInfo);
        setRequest(request);
    }

    @Override
    protected final NormalizedNodePayload emptyBody(final InstanceIdentifierContext path) {
        return NormalizedNodePayload.empty(path);
    }
}
