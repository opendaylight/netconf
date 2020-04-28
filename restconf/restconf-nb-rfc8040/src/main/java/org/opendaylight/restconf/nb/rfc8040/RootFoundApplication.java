/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.nb.rfc8040;

import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;
//import com.zte.sdn.oscp.restconf.traffic.control.RequestTrafficControl;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.XmlToXRDBodyWriter;
import org.opendaylight.restconf.nb.rfc8040.jersey.providers.errors.RestconfDocumentedExceptionMapper;
import org.opendaylight.restconf.nb.rfc8040.services.wrapper.RootFoundServiceImpl;


public class RootFoundApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return ImmutableSet.<Class<?>>builder().add(XmlToXRDBodyWriter.class)
                .add(RestconfDocumentedExceptionMapper.class)
//                .add(RequestTrafficControl.class)
                .build();
    }

    @Override
    public Set<Object> getSingletons() {
        final Set<Object> singletons = new HashSet<>();
        singletons.add(RootFoundServiceImpl.getInstance());
        return singletons;
    }
}