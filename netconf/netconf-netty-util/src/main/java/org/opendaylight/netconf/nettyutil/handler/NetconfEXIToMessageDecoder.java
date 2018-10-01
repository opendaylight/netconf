/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static java.util.Objects.requireNonNull;

import com.siemens.ct.exi.core.exceptions.EXIException;
import com.siemens.ct.exi.main.api.sax.SAXDecoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
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

    private static final Field SAXDECODER_DECODER;

    static {
        SAXDECODER_DECODER = AccessController.doPrivileged((PrivilegedAction<Field>)() -> {
            final Field f;
            try {
                f = SAXDecoder.class.getDeclaredField("decoder");
            } catch (NoSuchFieldException e) {
                // FIXME: this will fail consistently with EXIficient>=1.0.2. When we upgrade to 1.0.2 we can safely
                //        remove this entire machinery.
                LOG.warn("Unrecognised EXIficient version is present, skipping SAXDecoder.decoder cleanup", e);
                return null;
            }

            f.setAccessible(true);
            return f;
        });
    }

    // FIXME: remove this once we have a way to do buffer management
    private static final int CBUFFER_CACHE_SIZE = 4096;
    private static final int CBUFFER_CACHE_MAX = 65536;
    private static final ThreadLocal<char[]> CBUFFER_CACHE;
    private static final Field SAXDECODER_CBUFFER;

    static {
        SAXDECODER_CBUFFER = AccessController.doPrivileged((PrivilegedAction<Field>)() -> {
            final Field f;
            try {
                f = SAXDecoder.class.getDeclaredField("cbuffer");
            } catch (NoSuchFieldException e) {
                LOG.warn("Unrecognised EXIficient version is present, skipping SAXDecoder.cbuffer cleanup", e);
                return null;
            }

            f.setAccessible(true);
            return f;
        });

        CBUFFER_CACHE = SAXDECODER_CBUFFER == null ? null : ThreadLocal.withInitial(() -> new char[CBUFFER_CACHE_SIZE]);
    }

    /**
     * This class is not marked as shared, so it can be attached to only a single channel,
     * which means that {@link #decode(ChannelHandlerContext, ByteBuf, List)}
     * cannot be invoked concurrently. Hence we can reuse the reader.
     */
    private final XMLReader reader;
    private final DocumentBuilder documentBuilder;

    private NetconfEXIToMessageDecoder(final XMLReader reader) {
        this.reader = requireNonNull(reader);
        this.documentBuilder = UntrustedXML.newDocumentBuilder();

        if (reader instanceof SAXDecoder && SAXDECODER_CBUFFER != null) {
            try {
                SAXDECODER_CBUFFER.set(reader, null);
            } catch (IllegalAccessException e) {
                LOG.debug("Failed to clean up cbuffer in {}", reader, e);
            }
        }
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
            setupCbuffer();
            try {
                try {
                    reader.parse(new InputSource(is));
                } finally {
                    cleanupDecoder();
                }
            } finally {
                cleanupCbuffer();
            }
        }

        out.add(new NetconfMessage((Document) domResult.getNode()));
    }


    /**
     * EXIficient has a pre-allocated char[] buffer. This method sets it up so that we retain this state on a per-thread
     * basis.
     */
    private void setupCbuffer() {
        if (reader instanceof SAXDecoder && SAXDECODER_CBUFFER != null) {
            try {
                SAXDECODER_CBUFFER.set(reader, CBUFFER_CACHE.get());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to set cbuffer in " + reader, e);
            }
        }
    }

    private void cleanupCbuffer() {
        if (reader instanceof SAXDecoder && SAXDECODER_CBUFFER != null) {
            final char[] buffer;
            try {
                buffer = (char[]) SAXDECODER_DECODER.get(reader);
                SAXDECODER_DECODER.set(reader, null);
            } catch (IllegalAccessException e) {
                // This should never happen, but if it does, it is not really a problem
                LOG.debug("Failed to reset cbuffer in {}", reader, e);
                return;
            }

            if (buffer != null && buffer.length <= CBUFFER_CACHE_MAX) {
                CBUFFER_CACHE.set(buffer);
            }
        }
    }

    private void cleanupDecoder() {
        // See https://github.com/EXIficient/exificient/pull/22
        if (reader instanceof SAXDecoder && SAXDECODER_DECODER != null) {
            try {
                SAXDECODER_DECODER.set(reader, null);
            } catch (IllegalAccessException e) {
                // This should never happen, but if it does, it is not really a problem
                LOG.debug("Failed to reset decoder in {}", reader, e);
            }
        }
    }
}
