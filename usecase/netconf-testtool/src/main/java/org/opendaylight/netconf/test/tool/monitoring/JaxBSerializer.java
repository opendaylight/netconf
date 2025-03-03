/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.test.tool.monitoring;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.transform.dom.DOMResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

// FIXME: this is a rather ugly class
public final class JaxBSerializer {
    private static final JAXBContext JAXB_CONTEXT;

    static {
        try {
            JAXB_CONTEXT = JAXBContext.newInstance(NetconfState.class);
        } catch (final JAXBException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private JaxBSerializer() {
        // Hidden on purpose
    }

    public static Element toXml(final NetconfState monitoringModel) {
        final var res = new DOMResult();
        try {
            final var marshaller = JAXB_CONTEXT.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            marshaller.marshal(monitoringModel, res);
        } catch (JAXBException e) {
            throw new IllegalStateException("Unable to serialize netconf state " + monitoringModel, e);
        }
        return ((Document) res.getNode()).getDocumentElement();
    }
}
