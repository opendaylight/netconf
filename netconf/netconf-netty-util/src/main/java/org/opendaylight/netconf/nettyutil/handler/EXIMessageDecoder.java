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
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.codec.MessageDecoder;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

final class EXIMessageDecoder extends MessageDecoder {
    private static final Logger LOG = LoggerFactory.getLogger(EXIMessageDecoder.class);

    private static final SAXTransformerFactory FACTORY;

    static {
        final var f = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
        if (!f.getFeature(SAXTransformerFactory.FEATURE)) {
            throw new TransformerFactoryConfigurationError(
                    String.format("Factory %s is not a SAXTransformerFactory", f));
        }

        FACTORY = f;
    }

    /**
     * This class is not marked as shared, so it can be attached to only a single channel, which means that
     * {@link #decode(ChannelHandlerContext, ByteBuf, List)} cannot be invoked concurrently. Hence we can reuse the
     * reader.
     */
    private final DocumentBuilder documentBuilder = UntrustedXML.newDocumentBuilder();
    private final ThreadLocalSAXDecoder reader;

    EXIMessageDecoder(final ThreadLocalSAXDecoder reader) {
        this.reader = requireNonNull(reader);
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out)
            throws IOException, SAXException, TransformerConfigurationException {
        /*
         * Note that we could loop here and process all the messages, but we can't do that.
         * The reason is <stop-exi> operation, which has the contract of immediately stopping
         * the use of EXI, which means the next message needs to be decoded not by us, but rather
         * by the XML decoder.
         */

        // If empty Byte buffer is passed to r.parse, EOFException is thrown
        if (!in.isReadable()) {
            LOG.debug("No more content in incoming buffer.");
            return;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Received to decode: {}", ByteBufUtil.hexDump(in));
        }

        final var handler = FACTORY.newTransformerHandler();
        reader.setContentHandler(handler);

        final var domResult = new DOMResult(documentBuilder.newDocument());
        handler.setResult(domResult);

        try (var is = new ByteBufInputStream(in)) {
            // Performs internal reset before doing anything
            reader.parse(new InputSource(is));
        }

        out.add(new NetconfMessage((Document) domResult.getNode()));
    }
}
