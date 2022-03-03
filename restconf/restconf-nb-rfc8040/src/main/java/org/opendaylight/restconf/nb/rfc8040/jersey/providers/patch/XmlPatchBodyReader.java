/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers.patch;

import static com.google.common.base.Verify.verify;
import static com.google.common.base.Verify.verifyNotNull;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.Provider;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchEditOperation;
import org.opendaylight.restconf.common.patch.PatchEntity;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.meta.EffectiveStatement;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack.Inference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@Provider
@Consumes(MediaTypes.APPLICATION_YANG_PATCH_XML)
public class XmlPatchBodyReader extends AbstractPatchBodyReader {
    private static final Logger LOG = LoggerFactory.getLogger(XmlPatchBodyReader.class);

    public XmlPatchBodyReader(final SchemaContextHandler schemaContextHandler,
            final DOMMountPointService mountPointService) {
        super(schemaContextHandler, mountPointService);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected PatchContext readBody(final InstanceIdentifierContext<?> path, final InputStream entityStream)
            throws WebApplicationException {
        try {
            final Document doc = UntrustedXML.newDocumentBuilder().parse(entityStream);
            return parse(path, doc);
        } catch (final RestconfDocumentedException e) {
            throw e;
        } catch (final Exception e) {
            LOG.debug("Error parsing xml input", e);

            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, e);
        }
    }

    private static PatchContext parse(final InstanceIdentifierContext<?> pathContext, final Document doc)
            throws XMLStreamException, IOException, ParserConfigurationException, SAXException, URISyntaxException {
        final List<PatchEntity> resultCollection = new ArrayList<>();
        final String patchId = doc.getElementsByTagName("patch-id").item(0).getFirstChild().getNodeValue();
        final NodeList editNodes = doc.getElementsByTagName("edit");

        for (int i = 0; i < editNodes.getLength(); i++) {
            DataSchemaNode schemaNode = (DataSchemaNode) pathContext.getSchemaNode();
            final Element element = (Element) editNodes.item(i);
            final String operation = element.getElementsByTagName("operation").item(0).getFirstChild().getNodeValue();
            final PatchEditOperation oper = PatchEditOperation.valueOf(operation.toUpperCase(Locale.ROOT));
            final String editId = element.getElementsByTagName("edit-id").item(0).getFirstChild().getNodeValue();
            final String target = element.getElementsByTagName("target").item(0).getFirstChild().getNodeValue();
            final List<Element> values = readValueNodes(element, oper);
            final Element firstValueElement = values != null ? values.get(0) : null;

            // find complete path to target and target schema node
            // target can be also empty (only slash)
            YangInstanceIdentifier targetII;
            final SchemaNode targetNode;
            final Inference inference;
            if (target.equals("/")) {
                targetII = pathContext.getInstanceIdentifier();
                targetNode = pathContext.getSchemaContext();
                inference = Inference.ofDataTreePath(pathContext.getSchemaContext(), schemaNode.getQName());
            } else {
                // interpret as simple context
                targetII = ParserIdentifier.parserPatchTarget(pathContext, target);

                // move schema node
                schemaNode = verifyNotNull(DataSchemaContextTree.from(pathContext.getSchemaContext())
                    .findChild(targetII).orElseThrow().getDataSchemaNode());

                final SchemaInferenceStack stack = SchemaInferenceStack.of(pathContext.getSchemaContext());
                targetII.getPathArguments().stream()
                        .filter(arg -> !(arg instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates))
                        .filter(arg -> !(arg instanceof YangInstanceIdentifier.AugmentationIdentifier))
                        .forEach(p -> stack.enterSchemaTree(p.getNodeType()));
                final EffectiveStatement<?, ?> parentStmt = stack.exit();
                verify(parentStmt instanceof SchemaNode, "Unexpected parent %s", parentStmt);
                targetNode = (SchemaNode) parentStmt;
                inference = stack.toInference();
            }

            if (targetNode == null) {
                LOG.debug("Target node {} not found in path {} ", target, pathContext.getSchemaNode());
                throw new RestconfDocumentedException("Error parsing input", ErrorType.PROTOCOL,
                        ErrorTag.MALFORMED_MESSAGE);
            }

            if (oper.isWithValue()) {
                final NormalizedNode parsed;
                if (schemaNode instanceof  ContainerSchemaNode || schemaNode instanceof ListSchemaNode) {
                    final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
                    final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);

                    final XmlParserStream xmlParser = XmlParserStream.create(writer, inference);
                    xmlParser.traverse(new DOMSource(firstValueElement));
                    parsed = resultHolder.getResult();
                } else {
                    parsed = null;
                }

                // for lists allow to manipulate with list items through their parent
                if (targetII.getLastPathArgument() instanceof NodeIdentifierWithPredicates) {
                    targetII = targetII.getParent();
                }

                resultCollection.add(new PatchEntity(editId, oper, targetII, parsed));
            } else {
                resultCollection.add(new PatchEntity(editId, oper, targetII));
            }
        }

        return new PatchContext(pathContext, ImmutableList.copyOf(resultCollection), patchId);
    }

    /**
     * Read value nodes.
     *
     * @param element Element of current edit operation
     * @param operation Name of current operation
     * @return List of value elements
     */
    private static List<Element> readValueNodes(final @NonNull Element element,
            final @NonNull PatchEditOperation operation) {
        final Node valueNode = element.getElementsByTagName("value").item(0);

        if (operation.isWithValue() && valueNode == null) {
            throw new RestconfDocumentedException("Error parsing input",
                    ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }

        if (!operation.isWithValue() && valueNode != null) {
            throw new RestconfDocumentedException("Error parsing input",
                    ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }

        if (valueNode == null) {
            return null;
        }

        final List<Element> result = new ArrayList<>();
        final NodeList childNodes = valueNode.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            if (childNodes.item(i) instanceof Element) {
                result.add((Element) childNodes.item(i));
            }
        }

        return result;
    }
}
