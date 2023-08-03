/*
 * Copyright (c) 2020 ZTE Corp. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import java.util.Set;
import javax.ws.rs.core.Application;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors.RestconfDocumentedExceptionMapper;
import org.opendaylight.restconf.nb.rfc8040.rests.services.impl.RootResourceDiscoveryServiceImpl;

public class RootFoundApplication extends Application {
    private final RootResourceDiscoveryServiceImpl rrds;

    public RootFoundApplication(final String restconfRoot) {
        rrds = new RootResourceDiscoveryServiceImpl(restconfRoot);
    }

    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(RestconfDocumentedExceptionMapper.class);
    }

    @Override
    public Set<Object> getSingletons() {
        return Set.of(rrds);
    }
}
