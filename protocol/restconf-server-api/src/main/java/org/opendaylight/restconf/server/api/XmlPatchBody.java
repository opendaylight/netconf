/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2023 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.patch.rev170222.yang.patch.yang.patch.Edit.Operation;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public final class XmlPatchBody extends PatchBody {
    private static final Logger LOG = LoggerFactory.getLogger(XmlPatchBody.class);

    public XmlPatchBody(final InputStream inputStream) {
        super(inputStream);
    }

    @Override
    PatchContext toPatchContext(final ResourceContext resource, final InputStream inputStream)
            throws IOException, RequestException {
        try {
            return parse(resource, UntrustedXML.newDocumentBuilder().parse(inputStream));
        } catch (XMLStreamException | SAXException | URISyntaxException e) {
            LOG.debug("Failed to parse YANG Patch XML", e);
            throw new RequestException(ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE,
                "Error parsing YANG Patch XML: " + e.getMessage(), e);
        }
    }

    private static @NonNull PatchContext parse(final ResourceContext resource, final Document doc)
            throws IOException, RequestException, XMLStreamException, SAXException, URISyntaxException {
        final var entities = ImmutableList.<PatchEntity>builder();
        final var patchId = requireNonNullValue(doc.getElementsByTagName("patch-id").item(0), "patch-id")
            .getFirstChild().getNodeValue();
        final var editNodes = doc.getElementsByTagName("edit");

        for (int i = 0; i < editNodes.getLength(); i++) {
            final Element element = (Element) editNodes.item(i);
            final String operation = requireNonNullValue(element.getElementsByTagName("operation").item(0), "operation")
                .getFirstChild().getNodeValue();
            final Operation oper;
            try {
                oper = Operation.ofName(operation);
            } catch (IllegalArgumentException e) {
                LOG.error("Provided operation type {} does not match", operation, e);
                throw new RequestException(ErrorType.APPLICATION, ErrorTag.INVALID_VALUE,
                    "Operation value is incorrect: " + e.getMessage(), e);
            }
            final String editId = requireNonNullValue(element.getElementsByTagName("edit-id").item(0), "edit-id")
                .getFirstChild().getNodeValue();
            final String target = requireNonNullValue(element.getElementsByTagName("target").item(0), "target")
                .getFirstChild().getNodeValue();
            final List<Element> values = readValueNodes(element, oper);
            final Element firstValueElement = values != null ? values.get(0) : null;

            // find complete path to target, it can be also empty (only slash)
            final var targetData = parsePatchTarget(resource, target);
            final var inference = targetData.inference();
            final var stack = inference.toSchemaInferenceStack();
            if (!stack.isEmpty()) {
                stack.exit();
            }

            final var targetPath = targetData.instance();

            if (requiresValue(oper)) {
                final var resultHolder = new NormalizationResultHolder();
                final var writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
                final var xmlParser = XmlParserStream.create(writer, resource.path.databind().xmlCodecs(), inference);
                xmlParser.traverse(new DOMSource(firstValueElement));

                final var result = resultHolder.getResult().data();
                // for lists/leaf-list allow to manipulate with items through their parent
                if (targetPath.getLastPathArgument() instanceof NodeIdentifierWithPredicates
                        || targetPath.getLastPathArgument() instanceof NodeWithValue<?>) {
                    entities.add(new PatchEntity(editId, oper, targetPath.getParent(), result));
                } else {
                    entities.add(new PatchEntity(editId, oper, targetPath, result));
                }
            } else {
                entities.add(new PatchEntity(editId, oper, targetPath));
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
    private static List<Element> readValueNodes(final @NonNull Element element, final @NonNull Operation operation)
            throws RequestException {
        final var valueNode = element.getElementsByTagName("value").item(0);

        if (checkDataPresence(operation, valueNode != null)) {
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
            if (result.isEmpty()) {
                throw new RequestException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT,
                    "Empty 'value' element is not allowed");
            }
            return result;
        }
        if (requiresValue(operation)) {
            throw new RequestException(ErrorType.APPLICATION, ErrorTag.MISSING_ELEMENT, operation
                + " operation requires 'value' element");
        }
        throw new RequestException(ErrorType.APPLICATION, ErrorTag.UNKNOWN_ELEMENT, operation
            + " operation can not have 'value' element");
    }
}