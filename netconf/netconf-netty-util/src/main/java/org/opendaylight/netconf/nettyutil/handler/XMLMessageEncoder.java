/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.codec.MessageEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Comment;

public class XMLMessageEncoder extends MessageEncoder {
    private static final Logger LOG = LoggerFactory.getLogger(XMLMessageEncoder.class);

    private final @Nullable String clientId;

    public XMLMessageEncoder() {
        this((String) null);
    }

    public XMLMessageEncoder(final @Nullable String clientId) {
        this.clientId = clientId;
    }

    @Deprecated(since = "8.0.0", forRemoval = true)
    public XMLMessageEncoder(final Optional<String> clientId) {
        this(clientId.orElse(null));
    }

    @Override
    @VisibleForTesting
    public void encode(final ChannelHandlerContext ctx, final NetconfMessage msg, final ByteBuf out)
            throws IOException, TransformerException {
        LOG.trace("Sent to encode : {}", msg);

        if (clientId != null) {
            Comment comment = msg.getDocument().createComment("clientId:" + clientId);
            msg.getDocument().appendChild(comment);
        }

        try (OutputStream os = new ByteBufOutputStream(out)) {
            // Wrap OutputStreamWriter with BufferedWriter as suggested in javadoc for OutputStreamWriter

            // Using custom BufferedWriter that does not provide newLine method as performance improvement
            // see javadoc for BufferedWriter
            StreamResult result =
                    new StreamResult(new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8)));
            DOMSource source = new DOMSource(msg.getDocument());
            ThreadLocalTransformers.getPrettyTransformer().transform(source, result);
        }
    }
}
