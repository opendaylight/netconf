/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint;

import java.util.List;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public interface RestconfDevice extends AutoCloseable {

    /**
     * @return device data broker
     */
    DOMRpcService getRpcService();

    /**
     * @return device data broker
     */
    DOMDataBroker getDataBroker();

    /**
     * @return device notification service
     */
    DOMNotificationService getNotificationService();

    /**
     * @return device schema context
     */
    SchemaContext getSchemaContext();

    /**
     * @return device id
     */
    RestconfDeviceId getDeviceId();

    /**
     * @return modules supported by the device
     */
    List<Module> getSupportedModules();
}
