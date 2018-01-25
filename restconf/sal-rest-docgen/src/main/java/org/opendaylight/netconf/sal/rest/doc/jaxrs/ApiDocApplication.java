/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.jaxrs;

import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;
import org.opendaylight.aaa.provider.GsonProvider;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl;

public class ApiDocApplication extends Application {
    @Override
    public Set<Object> getSingletons() {
        Set<Object> singletons = new HashSet<>();
        singletons.add(ApiDocServiceImpl.getInstance());
        singletons.add(new JaxbContextResolver());
        singletons.add(new GsonProvider());
        return singletons;
    }
}
