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
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.spi.AbstractIdentifierAwareJaxRsProvider;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;

/**
 * Common superclass for readers producing {@link NormalizedNodeContext}.
 */
abstract class AbstractNormalizedNodeBodyReader extends AbstractIdentifierAwareJaxRsProvider<NormalizedNodeContext> {
    AbstractNormalizedNodeBodyReader(final ParserIdentifier parserIdentifier) {
        super(parserIdentifier);
    }

    public final void injectParams(final UriInfo uriInfo, final Request request) {
        setUriInfo(uriInfo);
        setRequest(request);
    }

    @Override
    protected final NormalizedNodeContext emptyBody(final InstanceIdentifierContext<?> path) {
        return new NormalizedNodeContext(path, null);
    }
}
