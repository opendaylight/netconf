/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import com.google.common.annotations.VisibleForTesting;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.codec.MessageEncoder;

public class XMLMessageEncoder extends MessageEncoder {
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
    public void encodeTo(final NetconfMessage msg, final OutputStream out) throws Exception {
        if (clientId != null) {
            final var doc = msg.getDocument();
            doc.appendChild(doc.createComment("clientId:" + clientId));
        }

        // Wrap OutputStreamWriter with BufferedWriter as suggested in javadoc for OutputStreamWriter

        // Using custom BufferedWriter that does not provide newLine method as performance improvement
        // see javadoc for BufferedWriter
        final var result = new StreamResult(new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)));
        final var source = new DOMSource(msg.getDocument());
        ThreadLocalTransformers.getPrettyTransformer().transform(source, result);
    }
}
