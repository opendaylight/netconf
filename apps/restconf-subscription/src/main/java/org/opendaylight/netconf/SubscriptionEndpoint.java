/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

@Component(factory = SubscriptionEndpoint.FACTORY_NAME, service = SubscriptionEndpoint.class)
public class SubscriptionEndpoint {
    public static final String FACTORY_NAME = "org.opendaylight.netconf.SubscriptionEndpoint";

//    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionEndpoint.class);
//    private static final String PROP_BOOTSTRAP_FACTORY = ".bootstrapFactory";
//    private static final String PROP_CONFIGURATION = ".configuration";

    @Activate
    public SubscriptionEndpoint() {
    }

    @Deactivate
    public void deactivate() {
    }
}
