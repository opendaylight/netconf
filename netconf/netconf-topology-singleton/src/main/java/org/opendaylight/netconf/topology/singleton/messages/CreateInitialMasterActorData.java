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
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;

/**
 * Master sends this message to the own actor to set necessary parameters.
 */
public class CreateInitialMasterActorData implements Serializable {

    private final DOMDataBroker deviceDataBroker;
    private final List<SourceIdentifier> allSourceIdentifiers;

    public CreateInitialMasterActorData(final DOMDataBroker deviceDataBroker,
                                        final List<SourceIdentifier> allSourceIdentifiers) {
        this.deviceDataBroker = deviceDataBroker;
        this.allSourceIdentifiers = allSourceIdentifiers;
    }

    public DOMDataBroker getDeviceDataBroker() {
        return deviceDataBroker;
    }

    public List<SourceIdentifier> getSourceIndentifiers() {
        return allSourceIdentifiers;
    }
}
