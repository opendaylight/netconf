/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.codec.MessageEncoder;
import org.opendaylight.netconf.shaded.exificient.core.exceptions.EXIException;
import org.opendaylight.netconf.shaded.exificient.main.api.sax.SAXFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class EXIMessageEncoder extends MessageEncoder {
    private static final Logger LOG = LoggerFactory.getLogger(EXIMessageEncoder.class);

    private final SAXFactory factory;

    EXIMessageEncoder(final SAXFactory factory) {
        this.factory = requireNonNull(factory);
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx, final NetconfMessage msg, final ByteBuf out)
            throws IOException, TransformerException, EXIException {
        LOG.trace("Sent to encode : {}", msg);

        try (var os = new ByteBufOutputStream(out)) {
            final var encoder = factory.createEXIWriter();
            encoder.setOutputStream(os);
            final var transformer = ThreadLocalTransformers.getDefaultTransformer();
            transformer.transform(new DOMSource(msg.getDocument()), new SAXResult(encoder));
        }
    }
}
