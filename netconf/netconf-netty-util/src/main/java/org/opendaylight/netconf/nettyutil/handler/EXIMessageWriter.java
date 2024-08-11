/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.OutputStream;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.codec.MessageWriter;
import org.opendaylight.netconf.shaded.exificient.core.exceptions.EXIException;
import org.opendaylight.netconf.shaded.exificient.main.api.sax.SAXEncoder;
import org.opendaylight.netconf.shaded.exificient.main.api.sax.SAXFactory;

final class EXIMessageWriter extends MessageWriter {
    private final SAXFactory factory;

    EXIMessageWriter(final SAXFactory factory) {
        super(false);
        this.factory = requireNonNull(factory);
    }

    @Override
    protected void writeTo(final NetconfMessage message, final OutputStream out)
            throws IOException, TransformerException {
        final SAXEncoder encoder;
        try {
            encoder = factory.createEXIWriter();
            encoder.setOutputStream(out);
        } catch (EXIException e) {
            throw new IOException(e);
        }
        threadLocalTransformer().transform(new DOMSource(message.getDocument()), new SAXResult(encoder));
    }
}
