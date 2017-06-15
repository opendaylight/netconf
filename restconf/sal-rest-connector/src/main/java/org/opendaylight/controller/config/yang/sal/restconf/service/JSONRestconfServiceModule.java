/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.sal.restconf.service;

import com.google.common.base.Optional;
import org.opendaylight.controller.config.api.osgi.WaitingServiceTracker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.sal.restconf.api.JSONRestconfService;
import org.opendaylight.yangtools.yang.common.OperationFailedException;
import org.osgi.framework.BundleContext;

@Deprecated
public class JSONRestconfServiceModule
        extends org.opendaylight.controller.config.yang.sal.restconf.service.AbstractJSONRestconfServiceModule {

    private BundleContext bundleContext;

    public JSONRestconfServiceModule(org.opendaylight.controller.config.api.ModuleIdentifier identifier,
                                     org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
    }

    public JSONRestconfServiceModule(
            org.opendaylight.controller.config.api.ModuleIdentifier identifier,
            org.opendaylight.controller.config.api.DependencyResolver dependencyResolver,
            org.opendaylight.controller.config.yang.sal.restconf.service.JSONRestconfServiceModule oldModule,
            java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        final WaitingServiceTracker<JSONRestconfService> tracker =
                WaitingServiceTracker.create(JSONRestconfService.class, bundleContext, "(type=default)");
        final JSONRestconfService service = tracker.waitForService(WaitingServiceTracker.FIVE_MINUTES);

        final class AutoCloseableJSONRestconfService implements JSONRestconfService, AutoCloseable {
            @Override
            public void close() {
                tracker.close();
            }

            @Override
            public void delete(String uriPath) throws OperationFailedException {
                service.delete(uriPath);
            }


            @Override
            public void put(String uriPath, String payload) throws OperationFailedException {
                service.put(uriPath, payload);
            }

            @Override
            public void post(String uriPath, String payload) throws OperationFailedException {
                service.post(uriPath, payload);
            }

            @Override
            public Optional<String> get(String uriPath, LogicalDatastoreType datastoreType)
                    throws OperationFailedException {
                return service.get(uriPath, datastoreType);
            }

            @Override
            public Optional<String> invokeRpc(String uriPath, Optional<String> input) throws OperationFailedException {
                return service.invokeRpc(uriPath, input);
            }
        }

        return new AutoCloseableJSONRestconfService();
    }

    public void setBundleContext(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }
}
