/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops.get;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import java.io.IOException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorSeverity;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorTag;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorType;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.ops.Datastore;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public abstract class AbstractGet extends AbstractSingletonNetconfOperation {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractGet.class);

    protected static final String FILTER = "filter";
    static final YangInstanceIdentifier ROOT = YangInstanceIdentifier.builder().build();
    protected final CurrentSchemaContext schemaContext;
    private final FilterContentValidator validator;

    public AbstractGet(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext) {
        super(netconfSessionIdForReporting);
        this.schemaContext = schemaContext;
        this.validator = new FilterContentValidator(schemaContext);
    }

    private static final XMLOutputFactory XML_OUTPUT_FACTORY;

    static {
        XML_OUTPUT_FACTORY = XMLOutputFactory.newFactory();
        XML_OUTPUT_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    protected Node transformNormalizedNode(final Document document, final NormalizedNode<?, ?> data, final YangInstanceIdentifier dataRoot) {

        final DOMResult result = new DOMResult(document.createElement(XmlNetconfConstants.DATA_KEY));

        final XMLStreamWriter xmlWriter = getXmlStreamWriter(result);

        final NormalizedNodeStreamWriter nnStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter,
                schemaContext.getCurrentContext(), getSchemaPath(dataRoot));

        final NormalizedNodeWriter nnWriter = NormalizedNodeWriter.forStreamWriter(nnStreamWriter, true);

        writeRootElement(xmlWriter, nnWriter, (ContainerNode) data);
        return result.getNode();
    }


    private XMLStreamWriter getXmlStreamWriter(final DOMResult result) {
        try {
            return XML_OUTPUT_FACTORY.createXMLStreamWriter(result);
        } catch (final XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Function<PathArgument, QName> PATH_ARG_TO_QNAME = new Function<YangInstanceIdentifier.PathArgument, QName>() {
        @Override
        public QName apply(final YangInstanceIdentifier.PathArgument input) {
            return input.getNodeType();
        }
    };

    private SchemaPath getSchemaPath(final YangInstanceIdentifier dataRoot) {
        return SchemaPath.create(Iterables.transform(dataRoot.getPathArguments(), PATH_ARG_TO_QNAME), dataRoot.equals(ROOT));
    }

    private void writeRootElement(final XMLStreamWriter xmlWriter, final NormalizedNodeWriter nnWriter, final ContainerNode data) {
        try {
            if (data.getNodeType().equals(SchemaContext.NAME)) {
                for (final DataContainerChild<? extends PathArgument, ?> child : data.getValue()) {
                    nnWriter.write(child);
                }
            } else {
                nnWriter.write(data);
            }
            nnWriter.flush();
            xmlWriter.flush();
        } catch (XMLStreamException | IOException e) {
            Throwables.propagate(e);
        }
    }

    protected Element serializeNodeWithParentStructure(final Document document, final YangInstanceIdentifier dataRoot, final NormalizedNode node) {
        if (!dataRoot.equals(ROOT)) {
            return (Element) transformNormalizedNode(document,
                    ImmutableNodes.fromInstanceId(schemaContext.getCurrentContext(), dataRoot, node),
                    ROOT);
        }
        return  (Element) transformNormalizedNode(document, node, ROOT);
    }

    /**
     *
     * @param operationElement operation element
     * @return if Filter is present and not empty returns Optional of the InstanceIdentifier to the read location in datastore.
     *          empty filter returns Optional.absent() which should equal an empty &lt;data/&gt; container in the response.
     *         if filter is not present we want to read the entire datastore - return ROOT.
     * @throws DocumentedException
     */
    protected Optional<YangInstanceIdentifier> getDataRootFromFilter(final XmlElement operationElement) throws DocumentedException {
        final Optional<XmlElement> filterElement = operationElement.getOnlyChildElementOptionally(FILTER);
        if (filterElement.isPresent()) {
            if (filterElement.get().getChildElements().size() == 0) {
                return Optional.absent();
            }
            return Optional.of(getInstanceIdentifierFromFilter(filterElement.get()));
        } else {
            return Optional.of(ROOT);
        }
    }

    @VisibleForTesting
    protected YangInstanceIdentifier getInstanceIdentifierFromFilter(final XmlElement filterElement) throws DocumentedException {

        if (filterElement.getChildElements().size() != 1) {
            throw new DocumentedException("Multiple filter roots not supported yet",
                    ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED, ErrorSeverity.ERROR);
        }

        final XmlElement element = filterElement.getOnlyChildElement();
        return validator.validate(element);
    }

    protected static final class GetConfigExecution {

        private final Optional<Datastore> datastore;
        public GetConfigExecution(final Optional<Datastore> datastore) {
            this.datastore = datastore;
        }

        public Optional<Datastore> getDatastore() {
            return datastore;
        }

        static GetConfigExecution fromXml(final XmlElement xml, final String operationName) throws DocumentedException {
            try {
                validateInputRpc(xml, operationName);
            } catch (final DocumentedException e) {
                throw new DocumentedException("Incorrect RPC: " + e.getMessage(), e.getErrorType(), e.getErrorTag(), e.getErrorSeverity(), e.getErrorInfo());
            }

            final Optional<Datastore> sourceDatastore;
            try {
                sourceDatastore = parseSource(xml);
            } catch (final DocumentedException e) {
                throw new DocumentedException("Get-config source attribute error: " + e.getMessage(), e.getErrorType(), e.getErrorTag(), e.getErrorSeverity(), e.getErrorInfo());
            }

            return new GetConfigExecution(sourceDatastore);
        }

        private static Optional<Datastore> parseSource(final XmlElement xml) throws DocumentedException {
            final Optional<XmlElement> sourceElement = xml.getOnlyChildElementOptionally(XmlNetconfConstants.SOURCE_KEY,
                    XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0);

            return  sourceElement.isPresent() ?
                    Optional.of(Datastore.valueOf(sourceElement.get().getOnlyChildElement().getName())) : Optional.absent();
        }

        private static void validateInputRpc(final XmlElement xml, final String operationName) throws DocumentedException {
            xml.checkName(operationName);
            xml.checkNamespace(XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0);
        }

    }

}
