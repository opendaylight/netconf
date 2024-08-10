/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static java.util.Objects.requireNonNull;

import java.io.OutputStream;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.codec.MessageEncoder;
import org.opendaylight.netconf.shaded.exificient.main.api.sax.SAXFactory;

final class EXIMessageEncoder extends MessageEncoder {
    private final SAXFactory factory;

    EXIMessageEncoder(final SAXFactory factory) {
        this.factory = requireNonNull(factory);
    }

    @Override
    protected void encodeTo(final NetconfMessage msg, final OutputStream out) throws Exception {
        final var encoder = factory.createEXIWriter();
        encoder.setOutputStream(out);
        final var transformer = ThreadLocalTransformers.getDefaultTransformer();
        transformer.transform(new DOMSource(msg.getDocument()), new SAXResult(encoder));
    }
}
