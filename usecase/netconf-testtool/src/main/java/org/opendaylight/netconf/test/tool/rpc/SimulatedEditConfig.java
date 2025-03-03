/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.rpc;

import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.server.api.operations.AbstractLastNetconfOperation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class SimulatedEditConfig extends AbstractLastNetconfOperation {
    private static final String DELETE_EDIT_CONFIG = "delete";
    private static final String OPERATION = "operation";
    private static final String REMOVE_EDIT_CONFIG = "remove";
    private final DataList storage;

    public SimulatedEditConfig(final SessionIdType sessionId, final DataList storage) {
        super(sessionId);
        this.storage = storage;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
            throws DocumentedException {
        final XmlElement configElementData = operationElement.getOnlyChildElement(XmlNetconfConstants.CONFIG_KEY);

        containsDelete(configElementData);
        if (containsDelete(configElementData)) {
            storage.resetConfigList();
        } else {
            storage.setConfigList(configElementData.getChildElements());
        }

        return document.createElement(XmlNetconfConstants.OK);
    }

    @Override
    protected String getOperationName() {
        return "edit-config";
    }

    private boolean containsDelete(final XmlElement element) {
        for (final Attr o : element.getAttributes().values()) {
            if (o.getLocalName().equals(OPERATION)
                && (o.getValue().equals(DELETE_EDIT_CONFIG) || o.getValue().equals(REMOVE_EDIT_CONFIG))) {
                return true;
            }
        }

        for (final XmlElement xmlElement : element.getChildElements()) {
            if (containsDelete(xmlElement)) {
                return true;
            }
        }

        return false;
    }
}
