/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology;

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.Collections;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.controller.sal.core.api.Provider;

/**
 * RestconfTopologyProvider obtains {@link DataBroker} and {@link DOMMountPointService} instances.
 */
public class RestconfTopologyProvider implements Provider, BindingAwareProvider {

    private DOMMountPointService mountPointService;
    private DataBroker dataBroker;

    @Override
    public void onSessionInitiated(final BindingAwareBroker.ProviderContext providerContext) {
        dataBroker = Preconditions.checkNotNull(providerContext.getSALService(DataBroker.class));
    }

    @Override
    public void onSessionInitiated(final Broker.ProviderSession session) {
        mountPointService = Preconditions.checkNotNull(session.getService(DOMMountPointService.class));
    }

    public DOMMountPointService getMountPointService() {
        return mountPointService;
    }

    public DataBroker getDataBroker() {
        return dataBroker;
    }

    @Override
    public Collection<ProviderFunctionality> getProviderFunctionality() {
        return Collections.emptySet();
    }
}
