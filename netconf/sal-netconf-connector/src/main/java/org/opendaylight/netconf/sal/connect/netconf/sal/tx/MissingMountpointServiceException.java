/*
 * Copyright (c) 2017 Pantheon Technologies s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceNotificationService;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalProvider;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * This exception is thrown by
 * {@link NetconfDeviceSalProvider.MountInstance#onTopologyDeviceConnected(SchemaContext,
 * DOMDataBroker, DOMRpcService, NetconfDeviceNotificationService)}
 * to indicate fatal clustering master mountpoint problem after which the mountpoint creation should fail.
 */
public class MissingMountpointServiceException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public MissingMountpointServiceException(final String message) {
        super(message);
    }
}
