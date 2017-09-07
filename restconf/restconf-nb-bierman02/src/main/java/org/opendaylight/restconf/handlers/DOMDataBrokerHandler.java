/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.handlers;

import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;

/**
 * {@link Deprecated} move to splitted module restconf-nb-rfc8040. Implementation of
 * {@link DOMDataBrokerHandler}.
 *
 */
@Deprecated
public class DOMDataBrokerHandler implements Handler<DOMDataBroker> {

    private final DOMDataBroker broker;

    public DOMDataBrokerHandler(final DOMDataBroker broker) {
        this.broker = broker;
    }

    @Override
    public DOMDataBroker get() {
        return this.broker;
    }

}
