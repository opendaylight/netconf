/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.transform.TransformerException;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.api.messages.NetconfMessage;

/**
 * Customized XMLMessageWriter that serializes additional header with session metadata along with {@link HelloMessage}.
 * Used by netconf clients to send information about the user, ip address, protocol etc.
 *
 * <p>Hello message with header example:
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
 *
 * @deprecated This class should not be used, as it constitutes data that is unexpected on a normal NETCONF session.
 */
@Deprecated(since = "8.0.1", forRemoval = true)
public final class HelloMessageWriter extends MessageWriter {
    private static final HelloMessageWriter DEFAULT = new HelloMessageWriter(false);
    private static final HelloMessageWriter PRETTY = new HelloMessageWriter(true);

    private final XMLMessageWriter delegate;

    private HelloMessageWriter(final boolean pretty) {
        super(pretty);
        delegate = XMLMessageWriter.of(pretty);
    }

    public static HelloMessageWriter of() {
        return DEFAULT;
    }

    public static HelloMessageWriter of(final boolean pretty) {
        return pretty ? PRETTY : DEFAULT;
    }

    public static HelloMessageWriter pretty() {
        return PRETTY;
    }

    @Override
    protected void writeTo(final NetconfMessage message, final OutputStream out)
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

        delegate.writeTo(message, out);
    }
}
