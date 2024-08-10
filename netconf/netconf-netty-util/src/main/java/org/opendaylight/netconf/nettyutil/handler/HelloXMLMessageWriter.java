/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.api.messages.NetconfMessage;

/**
 * Customized XMLMessageWriter that serializes additional header with session metadata along with {@link HelloMessage}.
 * Used by netconf clients to send information about the user, ip address, protocol etc.
 *
 * <p>
 * Hello message with header example:
 *
 * <p>
 *
 * <pre>
 * {@code
 * [tomas;10.0.0.0/10000;tcp;1000;1000;;/home/tomas;;]
 * < hello xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
 * < capabilities>
 * < capability>urn:ietf:params:netconf:base:1.0< /capability>
 * < /capabilities>
 * < /hello>
 * }
 * </pre>
 */
public final class HelloXMLMessageWriter extends XMLMessageWriter {
    public HelloXMLMessageWriter() {
        // Nothing else
    }

    public HelloXMLMessageWriter(final boolean pretty) {
        super(pretty);
    }

    @Override
    protected void writeMessage(final NetconfMessage message, final Transformer transformer, final OutputStream out)
            throws IOException, TransformerException {
        if (!(message instanceof HelloMessage hello)) {
            throw new IllegalStateException("Netconf message of type %s expected, was %s".formatted(
                HelloMessage.class, message.getClass()));
        }

        // If additional header present, serialize it along with netconf hello message
        final var header = hello.getAdditionalHeader();
        if (header.isPresent()) {
            out.write(header.orElseThrow().toFormattedString().getBytes(StandardCharsets.UTF_8));
        }

        super.writeMessage(message, transformer, out);
    }
}
