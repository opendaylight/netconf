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
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.codec.MessageWriter;

public sealed class XMLMessageWriter extends MessageWriter permits HelloXMLMessageWriter {
    public XMLMessageWriter() {
        this(true);
    }

    public XMLMessageWriter(final boolean pretty) {
        super(pretty);
    }

    @Override
    protected void writeMessage(final NetconfMessage message, final Transformer transformer, final OutputStream out)
            throws IOException, TransformerException {
        // Wrap OutputStreamWriter with BufferedWriter as suggested in javadoc for OutputStreamWriter

        // Using custom BufferedWriter that does not provide newLine method as performance improvement
        // see javadoc for BufferedWriter
        final var result = new StreamResult(new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)));
        final var source = new DOMSource(message.getDocument());
        transformer.transform(source, result);
    }
}
