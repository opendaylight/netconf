/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.codehaus.stax2.ri.dom.DOMWrappingReader;

// FIXME: use from yangtools when exposed
final class DOMSourceXMLStreamReader extends DOMWrappingReader {
    DOMSourceXMLStreamReader(final DOMSource src) throws XMLStreamException {
        super(src, true, true);
    }

    @Override
    public Object getProperty(final String name) {
        return null;
    }

    @Override
    public boolean isPropertySupported(final String name) {
        return false;
    }

    @Override
    public boolean setProperty(final String name, final Object value) {
        return false;
    }

    @Override
    protected void throwStreamException(final String msg, final Location loc) throws XMLStreamException {
        throw new XMLStreamException(msg, loc);
    }
}
