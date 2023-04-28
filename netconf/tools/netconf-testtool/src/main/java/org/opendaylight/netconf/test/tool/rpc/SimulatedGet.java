/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.rpc;

import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.server.api.operations.AbstractLastNetconfOperation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class SimulatedGet extends AbstractLastNetconfOperation {
    private final DataList storage;

    public SimulatedGet(final SessionIdType sessionId, final DataList storage) {
        super(sessionId);
        this.storage = storage;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement) {
        final Element element = document.createElement(XmlNetconfConstants.DATA_KEY);

        for (final XmlElement e : storage.getConfigList()) {
            final Element domElement = e.getDomElement();
            element.appendChild(element.getOwnerDocument().importNode(domElement, true));
        }

        return element;
    }

    @Override
    protected String getOperationName() {
        return XmlNetconfConstants.GET;
    }
}
