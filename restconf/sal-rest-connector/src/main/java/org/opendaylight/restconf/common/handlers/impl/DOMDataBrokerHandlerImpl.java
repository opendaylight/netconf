/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.handlers.impl;

import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.restconf.common.handlers.api.DOMDataBrokerHandler;

/**
 * Implementation of {@link DOMDataBrokerHandler}
 *
 */
public class DOMDataBrokerHandlerImpl implements DOMDataBrokerHandler {

    private DOMDataBroker broker;

    @Override
    public void setDOMDataBroker(final DOMDataBroker broker) {
        this.broker = broker;
    }

    @Override
    public DOMDataBroker getDOMDataBroker() {
        return this.broker;
    }

}
