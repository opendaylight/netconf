/*
 * Copyright (c) 2017 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.jersey.providers;

import com.google.common.annotations.Beta;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;

/**
 * Common superclass for readers producing {@link NormalizedNodeContext}.
 *
 * @author Robert Varga
 */
@Beta
public abstract class AbstractNormalizedNodeBodyReader
        extends AbstractIdentifierAwareJaxRsProvider<NormalizedNodeContext> {

    public final void injectParams(final UriInfo uriInfo, final Request request) {
        setUriInfo(uriInfo);
        setRequest(request);
    }

    @Override
    protected final NormalizedNodeContext emptyBody(final InstanceIdentifierContext<?> path) {
        return new NormalizedNodeContext(path, null);
    }
}
