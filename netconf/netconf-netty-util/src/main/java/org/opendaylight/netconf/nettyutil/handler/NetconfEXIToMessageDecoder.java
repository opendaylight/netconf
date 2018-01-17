/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import com.google.common.base.Preconditions;
import com.siemens.ct.exi.exceptions.EXIException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

public final class NetconfEXIToMessageDecoder extends ByteToMessageDecoder {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfEXIToMessageDecoder.class);
    private static final SAXTransformerFactory FACTORY;

    static {
        final TransformerFactory f = SAXTransformerFactory.newInstance();
        if (!f.getFeature(SAXTransformerFactory.FEATURE)) {
            throw new TransformerFactoryConfigurationError(
                    String.format("Factory %s is not a SAXTransformerFactory", f));
        }

        FACTORY = (SAXTransformerFactory)f;
    }

    /**
     * This class is not marked as shared, so it can be attached to only a single channel,
     * which means that {@link #decode(ChannelHandlerContext, ByteBuf, List)}
     * cannot be invoked concurrently. Hence we can reuse the reader.
     */
    private final XMLReader reader;
    private final DocumentBuilder documentBuilder;

    private NetconfEXIToMessageDecoder(final XMLReader reader) {
        this.reader = Preconditions.checkNotNull(reader);
        this.documentBuilder = UntrustedXML.newDocumentBuilder();
    }

    public static NetconfEXIToMessageDecoder create(final NetconfEXICodec codec) throws EXIException {
        return new NetconfEXIToMessageDecoder(codec.getReader());
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

        final TransformerHandler handler = FACTORY.newTransformerHandler();
        reader.setContentHandler(handler);

        final DOMResult domResult = new DOMResult(documentBuilder.newDocument());
        handler.setResult(domResult);

        try (InputStream is = new ByteBufInputStream(in)) {
            // Performs internal reset before doing anything
            reader.parse(new InputSource(is));
        }

        out.add(new NetconfMessage((Document) domResult.getNode()));
    }
}
