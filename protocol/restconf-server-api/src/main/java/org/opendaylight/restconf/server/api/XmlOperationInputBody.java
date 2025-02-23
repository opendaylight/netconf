/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import java.io.IOException;
import java.io.InputStream;
import javax.xml.stream.XMLStreamException;
import org.opendaylight.netconf.databind.DatabindPath.OperationPath;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class XmlOperationInputBody extends OperationInputBody {
    private static final Logger LOG = LoggerFactory.getLogger(XmlOperationInputBody.class);

    public XmlOperationInputBody(final InputStream inputStream) {
        super(inputStream);
    }

    @Override
    void streamTo(final OperationPath path, final InputStream inputStream, final NormalizedNodeStreamWriter writer)
            throws ServerException {
        final var stack = path.inference().toSchemaInferenceStack();
        stack.enterDataTree(path.inputStatement().argument());

        try {
            XmlParserStream.create(writer, path.databind().xmlCodecs(), stack.toInference())
                .parse(UntrustedXML.createXMLStreamReader(inputStream));
        } catch (IllegalArgumentException | IOException | XMLStreamException e) {
            LOG.debug("Error parsing XML input", e);
            throw newProtocolMalformedMessageServerException(path, "Invalid XML", e);
        }
    }
}
