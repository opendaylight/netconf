/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import java.io.IOException;
import java.io.InputStream;
import javax.xml.stream.XMLStreamException;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class XmlOperationInputBody extends OperationInputBody {
    private static final Logger LOG = LoggerFactory.getLogger(XmlOperationInputBody.class);

    public XmlOperationInputBody(final InputStream inputStream) {
        super(inputStream);
    }

    @Override
    void streamTo(final InputStream inputStream, final Inference inference, final NormalizedNodeStreamWriter writer)
            throws IOException {
        // Adjust inference to point to input
        final var stack = inference.toSchemaInferenceStack();
        stack.enterDataTree(extractInputQName(stack));

        try {
            XmlParserStream.create(writer, stack.toInference()).parse(UntrustedXML.createXMLStreamReader(inputStream));
        } catch (XMLStreamException e) {
            LOG.debug("Error parsing XML input", e);
            throwIfYangError(e);
            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, e);
        }
    }
}
