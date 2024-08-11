/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2024 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.api.messages.NetconfMessage;

@NonNullByDefault
public final class XMLMessageWriter extends MessageWriter {
    private static final XMLMessageWriter DEFAULT = new XMLMessageWriter(false);
    private static final XMLMessageWriter PRETTY = new XMLMessageWriter(true);

    private XMLMessageWriter(final boolean pretty) {
        super(pretty);
    }

    public static XMLMessageWriter of() {
        return DEFAULT;
    }

    public static XMLMessageWriter of(final boolean pretty) {
        return pretty ? PRETTY : DEFAULT;
    }

    public static XMLMessageWriter pretty() {
        return PRETTY;
    }

    @Override
    protected void writeTo(final NetconfMessage message, final OutputStream out)
            throws IOException, TransformerException {
        // Wrap OutputStreamWriter with BufferedWriter as suggested in javadoc for OutputStreamWriter

        // Using custom BufferedWriter that does not provide newLine method as performance improvement
        // see javadoc for BufferedWriter
        final var result = new StreamResult(new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)));
        final var source = new DOMSource(message.getDocument());
        threadLocalTransformer().transform(source, result);
    }
}
