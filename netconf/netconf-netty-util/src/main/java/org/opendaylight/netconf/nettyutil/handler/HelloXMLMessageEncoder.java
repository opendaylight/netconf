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
import java.nio.charset.StandardCharsets;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.api.messages.NetconfMessage;

/**
 * Customized NetconfMessageToXMLEncoder that serializes additional header with
 * session metadata along with
 * {@link HelloMessage}
 * . Used by netconf clients to send information about the user, ip address,
 * protocol etc.
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
public final class HelloXMLMessageEncoder extends XMLMessageEncoder {
    @Override
    @VisibleForTesting
    public void encodeTo(final NetconfMessage msg, final OutputStream out) throws Exception {
        if (!(msg instanceof HelloMessage hello)) {
            throw new IllegalStateException("Netconf message of type %s expected, was %s".formatted(
                HelloMessage.class, msg.getClass()));
        }

        // If additional header present, serialize it along with netconf hello message
        final var header = hello.getAdditionalHeader();
        if (header.isPresent()) {
            out.write(header.orElseThrow().toFormattedString().getBytes(StandardCharsets.UTF_8));
        }

        super.encodeTo(msg, out);
    }
}
