/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.jersey.providers;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nonnull;
import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.Provider;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.restconf.Rfc8040;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchEditOperation;
import org.opendaylight.restconf.common.patch.PatchEntity;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@Provider
@Consumes({Rfc8040.MediaTypes.PATCH + RestconfConstants.XML})
public class XmlToPatchBodyReader extends AbstractToPatchBodyReader {
    private static final Logger LOG = LoggerFactory.getLogger(XmlToPatchBodyReader.class);
    private static final Splitter SLASH_SPLITTER = Splitter.on('/');

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected PatchContext readBody(final InstanceIdentifierContext<?> path, final InputStream entityStream)
            throws IOException, WebApplicationException {
        try {
            final Document doc = UntrustedXML.newDocumentBuilder().parse(entityStream);
            return parse(path, doc);
        } catch (final RestconfDocumentedException e) {
            throw e;
        } catch (final Exception e) {
            LOG.debug("Error parsing xml input", e);

            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE);
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
            final PatchEditOperation oper = PatchEditOperation.valueOf(operation.toUpperCase());
            final String editId = element.getElementsByTagName("edit-id").item(0).getFirstChild().getNodeValue();
            final String target = element.getElementsByTagName("target").item(0).getFirstChild().getNodeValue();
            final List<Element> values = readValueNodes(element, oper);
            final Element firstValueElement = values != null ? values.get(0) : null;

            // get namespace according to schema node from path context or value
            final String namespace = firstValueElement == null
                    ? schemaNode.getQName().getNamespace().toString() : firstValueElement.getNamespaceURI();

            // find module according to namespace
            final Module module = pathContext.getSchemaContext().findModuleByNamespace(
                    URI.create(namespace)).iterator().next();

            // initialize codec + set default prefix derived from module name
            final StringModuleInstanceIdentifierCodec codec = new StringModuleInstanceIdentifierCodec(
                    pathContext.getSchemaContext(), module.getName());

            // find complete path to target and target schema node
            // target can be also empty (only slash)
            YangInstanceIdentifier targetII;
            final SchemaNode targetNode;
            if (target.equals("/")) {
                targetII = pathContext.getInstanceIdentifier();
                targetNode = pathContext.getSchemaContext();
            } else {
                targetII = codec.deserialize(codec.serialize(pathContext.getInstanceIdentifier())
                        .concat(prepareNonCondXpath(schemaNode, target.replaceFirst("/", ""), firstValueElement,
                                namespace, module.getQNameModule().getFormattedRevision())));

                targetNode = SchemaContextUtil.findDataSchemaNode(pathContext.getSchemaContext(),
                        codec.getDataContextTree().getChild(targetII).getDataSchemaNode().getPath().getParent());

                // move schema node
                schemaNode = (DataSchemaNode) SchemaContextUtil.findDataSchemaNode(pathContext.getSchemaContext(),
                        codec.getDataContextTree().getChild(targetII).getDataSchemaNode().getPath());
            }

            if (targetNode == null) {
                LOG.debug("Target node {} not found in path {} ", target, pathContext.getSchemaNode());
                throw new RestconfDocumentedException("Error parsing input", ErrorType.PROTOCOL,
                        ErrorTag.MALFORMED_MESSAGE);
            }

            if (oper.isWithValue()) {
                final NormalizedNode<?, ?> parsed;
                if (schemaNode instanceof  ContainerSchemaNode || schemaNode instanceof ListSchemaNode) {
                    final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
                    final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
                    final XmlParserStream xmlParser = XmlParserStream.create(writer, pathContext.getSchemaContext(),
                            schemaNode);
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
    private static List<Element> readValueNodes(@Nonnull final Element element,
            @Nonnull final PatchEditOperation operation) {
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

    /**
     * Prepare non-conditional XPath suitable for deserialization with {@link StringModuleInstanceIdentifierCodec}.
     *
     * @param schemaNode Top schema node
     * @param target Edit operation target
     * @param value Element with value
     * @param namespace Module namespace
     * @param revision Module revision
     * @return Non-conditional XPath
     */
    private static String prepareNonCondXpath(@Nonnull final DataSchemaNode schemaNode, @Nonnull final String target,
            @Nonnull final Element value, @Nonnull final String namespace, @Nonnull final String revision) {
        final Iterator<String> args = SLASH_SPLITTER.split(target.substring(target.indexOf(':') + 1)).iterator();

        final StringBuilder nonCondXpath = new StringBuilder();
        SchemaNode childNode = schemaNode;

        while (args.hasNext()) {
            final String s = args.next();
            nonCondXpath.append("/");
            nonCondXpath.append(s);
            childNode = ((DataNodeContainer) childNode).getDataChildByName(QName.create(namespace, revision, s));

            if (childNode instanceof ListSchemaNode && args.hasNext()) {
                appendKeys(nonCondXpath, ((ListSchemaNode) childNode).getKeyDefinition().iterator(), args);
            }
        }

        if (childNode instanceof ListSchemaNode && value != null) {
            final Iterator<String> keyValues = readKeyValues(value,
                    ((ListSchemaNode) childNode).getKeyDefinition().iterator());
            appendKeys(nonCondXpath, ((ListSchemaNode) childNode).getKeyDefinition().iterator(), keyValues);
        }

        return nonCondXpath.toString();
    }

    /**
     * Read value for every list key.
     *
     * @param value Value element
     * @param keys Iterator of list keys names
     * @return Iterator of list keys values
     */
    private static Iterator<String> readKeyValues(@Nonnull final Element value, @Nonnull final Iterator<QName> keys) {
        final List<String> result = new ArrayList<>();

        while (keys.hasNext()) {
            result.add(value.getElementsByTagName(keys.next().getLocalName()).item(0).getFirstChild().getNodeValue());
        }

        return result.iterator();
    }

    /**
     * Append key name - key value pairs for every list key to {@code nonCondXpath}.
     *
     * @param nonCondXpath Builder for creating non-conditional XPath
     * @param keyNames Iterator of list keys names
     * @param keyValues Iterator of list keys values
     */
    private static void appendKeys(@Nonnull final StringBuilder nonCondXpath, @Nonnull final Iterator<QName> keyNames,
                            @Nonnull final Iterator<String> keyValues) {
        while (keyNames.hasNext()) {
            nonCondXpath.append('[');
            nonCondXpath.append(keyNames.next().getLocalName());
            nonCondXpath.append("='");
            nonCondXpath.append(keyValues.next());
            nonCondXpath.append("']");
        }
    }
}
