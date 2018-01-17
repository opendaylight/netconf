/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import com.google.common.base.Preconditions;
import com.siemens.ct.exi.api.sax.SAXEncoder;
import com.siemens.ct.exi.exceptions.EXIException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.io.IOException;
import java.io.OutputStream;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfMessageToEXIEncoder extends MessageToByteEncoder<NetconfMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfMessageToEXIEncoder.class);
    private final NetconfEXICodec codec;

    private NetconfMessageToEXIEncoder(final NetconfEXICodec codec) {
        this.codec = Preconditions.checkNotNull(codec);
    }

    public static NetconfMessageToEXIEncoder create(final NetconfEXICodec codec) {
        return new NetconfMessageToEXIEncoder(codec);
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx, final NetconfMessage msg, final ByteBuf out)
            throws IOException, TransformerException, EXIException {
        LOG.trace("Sent to encode : {}", msg);

        try (final OutputStream os = new ByteBufOutputStream(out)) {
            final SAXEncoder encoder = codec.getWriter();
            encoder.setOutputStream(os);
            final Transformer transformer = ThreadLocalTransformers.getDefaultTransformer();
            transformer.transform(new DOMSource(msg.getDocument()), new SAXResult(encoder));
        }
    }
}
