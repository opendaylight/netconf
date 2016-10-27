/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.messages;

import java.io.Serializable;
import java.util.List;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceNotificationService;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;

/**
 * Master sends this message to the own actor to set necessary parameters.
 */
public class CreateInitialMasterActorData implements Serializable {
    private static final long serialVersionUID = 1L;

    private final DOMDataBroker deviceDataBroker;
    private final List<SourceIdentifier> allSourceIdentifiers;
    private final DOMRpcService deviceRpc;
    private final NetconfDeviceNotificationService notificationService;

    public CreateInitialMasterActorData(final DOMDataBroker deviceDataBroker,
                                        final List<SourceIdentifier> allSourceIdentifiers,
                                        final DOMRpcService deviceRpc,
                                        final NetconfDeviceNotificationService notificationService) {
        this.deviceDataBroker = deviceDataBroker;
        this.allSourceIdentifiers = allSourceIdentifiers;
        this.deviceRpc = deviceRpc;
        this.notificationService = notificationService;
    }

    public DOMDataBroker getDeviceDataBroker() {
        return deviceDataBroker;
    }

    public List<SourceIdentifier> getSourceIndentifiers() {
        return allSourceIdentifiers;
    }

    public DOMRpcService getDeviceRpc() {
        return deviceRpc;
    }

    public NetconfDeviceNotificationService getNotificationService() {
        return notificationService;
    }
}
