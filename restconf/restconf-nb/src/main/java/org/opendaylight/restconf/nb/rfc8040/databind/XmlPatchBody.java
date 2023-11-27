/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2023 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchEntity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public final class XmlPatchBody extends PatchBody {
    private static final Logger LOG = LoggerFactory.getLogger(XmlPatchBody.class);

    public XmlPatchBody(final InputStream inputStream) {
        super(inputStream);
    }

    @Override
    PatchContext toPatchContext(final DatabindContext databind, final YangInstanceIdentifier urlPath,
            final InputStream inputStream) throws IOException {
        try {
            return parse(databind, urlPath, UntrustedXML.newDocumentBuilder().parse(inputStream));
        } catch (XMLStreamException | SAXException | URISyntaxException e) {
            LOG.debug("Failed to parse YANG Patch XML", e);
            throw new RestconfDocumentedException("Error parsing YANG Patch XML: " + e.getMessage(), ErrorType.PROTOCOL,
                ErrorTag.MALFORMED_MESSAGE, e);
        }
    }

    private static @NonNull PatchContext parse(final DatabindContext databind, final YangInstanceIdentifier urlPath,
            final Document doc) throws XMLStreamException, IOException, SAXException, URISyntaxException {
        final var entities = ImmutableList.<PatchEntity>builder();
        final var patchId = doc.getElementsByTagName("patch-id").item(0).getFirstChild().getNodeValue();
        final var editNodes = doc.getElementsByTagName("edit");

        for (int i = 0; i < editNodes.getLength(); i++) {
            final Element element = (Element) editNodes.item(i);
            final String operation = element.getElementsByTagName("operation").item(0).getFirstChild().getNodeValue();
            final Operation oper = Operation.ofName(operation);
            final String editId = element.getElementsByTagName("edit-id").item(0).getFirstChild().getNodeValue();
            final String target = element.getElementsByTagName("target").item(0).getFirstChild().getNodeValue();
            final List<Element> values = readValueNodes(element, oper);
            final Element firstValueElement = values != null ? values.get(0) : null;

            // find complete path to target, it can be also empty (only slash)
            final var targetII = parsePatchTarget(databind, urlPath, target);
            // move schema node
            final var lookup = databind.schemaTree().enterPath(targetII).orElseThrow();

            final var stack = lookup.stack();
            final var inference = stack.toInference();
            if (!stack.isEmpty()) {
                stack.exit();
            }

            if (requiresValue(oper)) {
                final var resultHolder = new NormalizationResultHolder();
                final var writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
                final var xmlParser = XmlParserStream.create(writer, inference);
                xmlParser.traverse(new DOMSource(firstValueElement));

                final var result = resultHolder.getResult().data();
                // for lists allow to manipulate with list items through their parent
                if (targetII.getLastPathArgument() instanceof NodeIdentifierWithPredicates) {
                    entities.add(new PatchEntity(editId, oper, targetII.getParent(), result));
                } else {
                    entities.add(new PatchEntity(editId, oper, targetII, result));
                }
            } else {
                entities.add(new PatchEntity(editId, oper, targetII));
            }
        }

        return new PatchContext(patchId, entities.build());
    }

    /**
     * Read value nodes.
     *
     * @param element Element of current edit operation
     * @param operation Name of current operation
     * @return List of value elements
     */
    private static List<Element> readValueNodes(final @NonNull Element element, final @NonNull Operation operation) {
        final Node valueNode = element.getElementsByTagName("value").item(0);

        final boolean isWithValue = requiresValue(operation);
        if (isWithValue && valueNode == null) {
            throw new RestconfDocumentedException("Error parsing input",
                    ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }

        if (!isWithValue && valueNode != null) {
            throw new RestconfDocumentedException("Error parsing input",
                    ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }

        if (valueNode == null) {
            return null;
        }

        final var result = new ArrayList<Element>();
        final var childNodes = valueNode.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            if (childNodes.item(i) instanceof Element childElement) {
                result.add(childElement);
            }
        }

        return result;
    }
}