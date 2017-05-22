/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops.get;

import static java.util.function.Function.identity;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorSeverity;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorTag;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorType;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.mdsal.connector.ops.Datastore;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.opendaylight.netconf.util.messages.SubtreeFilter;
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
import org.w3c.dom.NodeList;

public abstract class AbstractGet extends AbstractSingletonNetconfOperation {

    private static final XMLOutputFactory XML_OUTPUT_FACTORY;
    private static final YangInstanceIdentifier ROOT = YangInstanceIdentifier.EMPTY;
    private static final Logger LOG = LoggerFactory.getLogger(AbstractGet.class);

    static {
        XML_OUTPUT_FACTORY = XMLOutputFactory.newFactory();
        XML_OUTPUT_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    protected final CurrentSchemaContext schemaContext;
    private final FilterContentValidator validator;
    private final TransactionProvider transactionProvider;
    protected final Filter filter;

    AbstractGet(final String netconfSessionIdForReporting,
                final CurrentSchemaContext schemaContext,
                final TransactionProvider transactionProvider) {
        super(netconfSessionIdForReporting);
        this.schemaContext = schemaContext;
        this.validator = new FilterContentValidator(schemaContext);
        this.transactionProvider = transactionProvider;
        this.filter = new Filter(validator);
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
            throws DocumentedException {

        final Collection<Filter.YidFilter> rootToFilter = filter.getDataRootsFromFilter(operationElement);
        if (rootToFilter.isEmpty()) {
            return XmlUtil.createElement(document, XmlNetconfConstants.DATA_KEY, Optional.absent());
        }
        final Datastore datastore = getDatastore(operationElement);

        final DOMDataReadWriteTransaction rwTx = getTransaction(datastore);
        try {

            final Map<Filter.YidFilter, ListenableFuture<Optional<NormalizedNode<?, ?>>>> results =
                    rootToFilter.stream()
                            .collect(Collectors.toMap(identity(), entry -> read(rwTx, entry.getPath())));
            final List<ListenableFuture<Optional<Element>>> xmlResults = results.entrySet().stream()
                    .map(e -> toXmlFuture(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
            final ListenableFuture<List<Optional<Element>>> allFuture = Futures.allAsList(xmlResults);
            final Element resultElement =
                    XmlUtil.createElement(document, XmlNetconfConstants.DATA_KEY, Optional.absent());
            allFuture.get().stream()
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(n -> appendResult(resultElement, n));
            if (datastore == Datastore.running) {
                transactionProvider.abortRunningTransaction(rwTx);
            }
            return resultElement;
        } catch (final InterruptedException | ExecutionException e) {
            LOG.warn("Unable to read data: ", e);
            throw new IllegalStateException("Unable to read data ", e);
        }
    }

    abstract Datastore getDatastore(XmlElement operationElement) throws DocumentedException;

    abstract CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> read(DOMDataReadWriteTransaction rwTx,
                                                                                     YangInstanceIdentifier path);

    private DOMDataReadWriteTransaction getTransaction(final Datastore datastore) throws DocumentedException {
        if (datastore == Datastore.candidate) {
            return transactionProvider.getOrCreateTransaction();
        } else if (datastore == Datastore.running) {
            return transactionProvider.createRunningTransaction();
        }
        throw new DocumentedException("Incorrect Datastore: ", ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT,
                ErrorSeverity.ERROR);
    }

    private void appendResult(final Element result, final Element element) {
        final NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            final Node adopted = result.getOwnerDocument().adoptNode(childNodes.item(i));
            result.appendChild(adopted);
        }
    }

    private ListenableFuture<Optional<Element>> toXmlFuture(
            final Filter.YidFilter filter,
            final ListenableFuture<Optional<NormalizedNode<?, ?>>> data) {
        return Futures.transform(data,
                (Function<Optional<NormalizedNode<?, ?>>,
                        Optional<Element>>) nn -> toElement(nn, filter.getPath(), filter.getFilter()));
    }

    private Optional<Element> toElement(final Optional<NormalizedNode<?, ?>> nn,
                                        final YangInstanceIdentifier dataRoot,
                                        final XmlElement filter) {
        if (nn.isPresent()) {
            //TODO - remove XmlUtil.newDocument(), so no import node needed
            final Element result = serializeNodeWithParentStructure(XmlUtil.newDocument(), dataRoot, nn.get());
            try {
                return Optional.of(SubtreeFilter.filtered(filter, result));
            } catch (final DocumentedException e) {
                throw new UncheckedExecutionException(e);
            }
        }
        return Optional.absent();
    }

    private Node transformNormalizedNode(final Document document, final NormalizedNode<?, ?> data,
                                         final YangInstanceIdentifier dataRoot) {
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

    private SchemaPath getSchemaPath(final YangInstanceIdentifier dataRoot) {
        return SchemaPath.create(
                Iterables.transform(dataRoot.getPathArguments(), PathArgument::getNodeType), dataRoot.equals(ROOT));
    }

    private void writeRootElement(final XMLStreamWriter xmlWriter, final NormalizedNodeWriter nnWriter,
                                  final ContainerNode data) {
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

    private Element serializeNodeWithParentStructure(final Document document, final YangInstanceIdentifier dataRoot,
                                                     final NormalizedNode node) {
        if (!dataRoot.equals(ROOT)) {
            return (Element) transformNormalizedNode(document,
                    ImmutableNodes.fromInstanceId(schemaContext.getCurrentContext(), dataRoot, node),
                    ROOT);
        }
        return (Element) transformNormalizedNode(document, node, ROOT);
    }


    @VisibleForTesting
    protected YangInstanceIdentifier getInstanceIdentifierFromFilter(final XmlElement filterElement)
            throws DocumentedException {

        if (filterElement.getChildElements().size() != 1) {
            throw new DocumentedException("Multiple filter roots not supported yet",
                    ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED, ErrorSeverity.ERROR);
        }

        final XmlElement element = filterElement.getOnlyChildElement();
        return validator.validate(element);
    }

}
