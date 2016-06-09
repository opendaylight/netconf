/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.handlers.api;

import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;

/**
 * Handling DOM data broker.
 *
 */
public interface DOMDataBrokerHandler {

    /**
     * Set DOM data broker
     *
     * @param broker
     *            - {@link DOMDataBroker}
     */
    void setDOMDataBroker(DOMDataBroker broker);

    /**
     * Get DOM data broker
     *
     * @return {@link DOMDataBroker}
     */
    DOMDataBroker getDOMDataBroker();
}
