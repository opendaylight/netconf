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
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nonnull;
import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHEditOperation;
import org.opendaylight.netconf.sal.restconf.impl.PATCHEntity;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.Draft17;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XmlUtils;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.parser.DomToNormalizedNodeParserFactory;
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

@Provider
@Consumes({Draft17.MediaTypes.PATCH + RestconfConstants.XML})
public class XmlToPATCHBodyReader extends AbstractIdentifierAwareJaxRsProvider implements
        MessageBodyReader<PATCHContext> {

    private final static Logger LOG = LoggerFactory.getLogger(XmlToPATCHBodyReader.class);
    private static final DocumentBuilderFactory BUILDERFACTORY;

    static {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
        } catch (final ParserConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
        factory.setNamespaceAware(true);
        factory.setCoalescing(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);
        BUILDERFACTORY = factory;
    }

    @Override
    public boolean isReadable(final Class<?> type, final Type genericType,
                              final Annotation[] annotations, final MediaType mediaType) {
        return true;
    }

    @Override
    public PATCHContext readFrom(final Class<PATCHContext> type, final Type genericType,
                                 final Annotation[] annotations, final MediaType mediaType,
                                 final MultivaluedMap<String, String> httpHeaders, final InputStream entityStream)
            throws IOException, WebApplicationException {

        try {
            final InstanceIdentifierContext<?> path = getInstanceIdentifierContext();

            if (entityStream.available() < 1) {
                // represent empty nopayload input
                return new PATCHContext(path, null, null);
            }

            final DocumentBuilder dBuilder;
            try {
                dBuilder = BUILDERFACTORY.newDocumentBuilder();
            } catch (final ParserConfigurationException e) {
                throw new IllegalStateException("Failed to parse XML document", e);
            }
            final Document doc = dBuilder.parse(entityStream);

            return parse(path, doc);
        } catch (final RestconfDocumentedException e) {
            throw e;
        } catch (final Exception e) {
            LOG.debug("Error parsing xml input", e);

            throw new RestconfDocumentedException("Error parsing input: " + e.getMessage(), ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE);
        }
    }

    private PATCHContext parse(final InstanceIdentifierContext<?> pathContext, final Document doc) {
        final List<PATCHEntity> resultCollection = new ArrayList<>();
        final String patchId = doc.getElementsByTagName("patch-id").item(0).getFirstChild().getNodeValue();
        final NodeList editNodes = doc.getElementsByTagName("edit");
        final DomToNormalizedNodeParserFactory parserFactory =
                DomToNormalizedNodeParserFactory.getInstance(XmlUtils.DEFAULT_XML_CODEC_PROVIDER,
                        pathContext.getSchemaContext());

        for (int i = 0; i < editNodes.getLength(); i++) {
            DataSchemaNode schemaNode = (DataSchemaNode) pathContext.getSchemaNode();
            final Element element = (Element) editNodes.item(i);
            final String operation = element.getElementsByTagName("operation").item(0).getFirstChild().getNodeValue();
            final String editId = element.getElementsByTagName("edit-id").item(0).getFirstChild().getNodeValue();
            final String target = element.getElementsByTagName("target").item(0).getFirstChild().getNodeValue();
            final List<Element> values = readValueNodes(element, operation);
            final Element firstValueElement = values != null ? values.get(0) : null;

            // get namespace according to schema node from path context or value
            final String namespace = (firstValueElement == null) ?
                    schemaNode.getQName().getNamespace().toString() : firstValueElement.getNamespaceURI();

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
            } else {
                if (PATCHEditOperation.isPatchOperationWithValue(operation)) {
                    NormalizedNode<?, ?> parsed = null;
                    if (schemaNode instanceof ContainerSchemaNode) {
                        parsed = parserFactory.getContainerNodeParser().parse(values, (ContainerSchemaNode) schemaNode);
                    } else if (schemaNode instanceof ListSchemaNode) {
                        parsed = parserFactory.getMapNodeParser().parse(values, (ListSchemaNode) schemaNode);
                    }

                    // for lists allow to manipulate with list items through their parent
                    if (targetII.getLastPathArgument() instanceof NodeIdentifierWithPredicates) {
                        targetII = targetII.getParent();
                    }

                    resultCollection.add(new PATCHEntity(editId, operation, targetII, parsed));
                } else {
                    resultCollection.add(new PATCHEntity(editId, operation, targetII));
                }
            }
        }

        return new PATCHContext(pathContext, ImmutableList.copyOf(resultCollection), patchId);
    }

    /**
     * Read value nodes
     * @param element Element of current edit operation
     * @param operation Name of current operation
     * @return List of value elements
     */
    private List<Element> readValueNodes(@Nonnull final Element element, @Nonnull final String operation) {
        final Node valueNode = element.getElementsByTagName("value").item(0);

        if (PATCHEditOperation.isPatchOperationWithValue(operation) && valueNode == null) {
            throw new RestconfDocumentedException("Error parsing input",
                    ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }

        if (!PATCHEditOperation.isPatchOperationWithValue(operation) && valueNode != null) {
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
     * Prepare non-conditional XPath suitable for deserialization
     * with {@link StringModuleInstanceIdentifierCodec}
     * @param schemaNode Top schema node
     * @param target Edit operation target
     * @param value Element with value
     * @param namespace Module namespace
     * @param revision Module revision
     * @return Non-conditional XPath
     */
    private String prepareNonCondXpath(@Nonnull final DataSchemaNode schemaNode, @Nonnull final String target,
                                       @Nonnull final Element value, @Nonnull final String namespace,
                                       @Nonnull String revision) {
        final Iterator<String> args = Splitter.on("/").split(target.substring(target.indexOf(':') + 1)).iterator();

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
     * Read value for every list key
     * @param value Value element
     * @param keys Iterator of list keys names
     * @return Iterator of list keys values
     */
    private Iterator<String> readKeyValues(@Nonnull final Element value, @Nonnull final Iterator<QName> keys) {
        final List<String> result = new ArrayList<>();

        while (keys.hasNext()) {
            result.add(value.getElementsByTagName(keys.next().getLocalName()).item(0).getFirstChild().getNodeValue());
        }

        return result.iterator();
    }

    /**
     * Append key name - key value pairs for every list key to {@code nonCondXpath}
     * @param nonCondXpath Builder for creating non-conditional XPath
     * @param keyNames Iterator of list keys names
     * @param keyValues Iterator of list keys values
     */
    private void appendKeys(@Nonnull final StringBuilder nonCondXpath, @Nonnull final Iterator<QName> keyNames,
                            @Nonnull final Iterator<String> keyValues) {
        while (keyNames.hasNext()) {
            nonCondXpath.append("[");
            nonCondXpath.append(keyNames.next().getLocalName());
            nonCondXpath.append("=");
            nonCondXpath.append("'");
            nonCondXpath.append(keyValues.next());
            nonCondXpath.append("'");
            nonCondXpath.append("]");
        }
    }
}
