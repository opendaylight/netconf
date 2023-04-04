/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api;

import org.opendaylight.netconf.api.xml.XmlUtil;
import org.w3c.dom.Document;

/**
 * NetconfMessage represents a wrapper around org.w3c.dom.Document. Needed for
 * implementing ProtocolMessage interface.
 */
public class NetconfMessage {
    private final Document doc;

    public NetconfMessage() {
        // Required for FailedNetconfMessage
        doc = null;
    }

    public NetconfMessage(final Document doc) {
        this.doc = doc;
    }

    public Document getDocument() {
        return doc;
    }

    @Override
    public String toString() {
        return XmlUtil.toString(doc);
    }
}
