/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestconfActivator implements BundleActivator {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfActivator.class);

    @Override
    public void start(final BundleContext context) throws Exception {
        LOG.info("Start RestconfActivator.");
        final ServiceReference<DOMRpcService> domRpcServiceRef = context.getServiceReference(DOMRpcService.class);
        if (domRpcServiceRef != null) {
            final DOMRpcService domRpcService = context.getService(domRpcServiceRef);
            BrokerFacade.getInstance().setRpcService(domRpcService);
        } else {
            LOG.error("Missing DOMRpcService service.");
        }
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
    }

}
