/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.messages;

import java.util.List;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;

/**
 * Master sends this message to the own actor to set necessary parameters.
 */
public class CreateInitialMasterActorData {
    private final DOMDataBroker deviceDataBroker;
    private final NetconfDataTreeService netconfService;
    private final List<SourceIdentifier> allSourceIdentifiers;
    private final RemoteDeviceServices deviceServices;

    public CreateInitialMasterActorData(final DOMDataBroker deviceDataBroker,
                                        final NetconfDataTreeService netconfService,
                                        final List<SourceIdentifier> allSourceIdentifiers,
                                        final RemoteDeviceServices deviceServices) {
        this.deviceDataBroker = deviceDataBroker;
        this.netconfService = netconfService;
        this.allSourceIdentifiers = allSourceIdentifiers;
        this.deviceServices = deviceServices;
    }

    public DOMDataBroker getDeviceDataBroker() {
        return deviceDataBroker;
    }

    public NetconfDataTreeService getNetconfDataTreeService() {
        return netconfService;
    }

    public List<SourceIdentifier> getSourceIndentifiers() {
        return allSourceIdentifiers;
    }

    public RemoteDeviceServices getDeviceServices() {
        return deviceServices;
    }
}
