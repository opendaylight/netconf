/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorSeverity;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorTag;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorType;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.mdsal.connector.ops.DataTreeChangeTracker.DataTreeChange;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.DomUtils;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.parser.DomToNormalizedNodeParserFactory;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class EditConfig extends AbstractSingletonNetconfOperation {

    private static final Logger LOG = LoggerFactory.getLogger(EditConfig.class);

    private static final String OPERATION_NAME = "edit-config";
    private static final String CONFIG_KEY = "config";
    private static final String TARGET_KEY = "target";
    private static final String DEFAULT_OPERATION_KEY = "default-operation";
    private final CurrentSchemaContext schemaContext;
    private final TransactionProvider transactionProvider;

    public EditConfig(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext,
            final TransactionProvider transactionProvider) {
        super(netconfSessionIdForReporting);
        this.schemaContext = schemaContext;
        this.transactionProvider = transactionProvider;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
            throws DocumentedException {
        final Datastore targetDatastore = extractTargetParameter(operationElement);
        if (targetDatastore == Datastore.running) {
            throw new DocumentedException("edit-config on running datastore is not supported",
                    ErrorType.PROTOCOL,
                    ErrorTag.OPERATION_NOT_SUPPORTED,
                    ErrorSeverity.ERROR);
        }

        final ModifyAction defaultAction = getDefaultOperation(operationElement);

        final XmlElement configElement = getElement(operationElement, CONFIG_KEY);

        for (final XmlElement element : configElement.getChildElements()) {
            final String ns = element.getNamespace();
            final DataSchemaNode schemaNode = getSchemaNodeFromNamespace(ns, element).get();

            final DataTreeChangeTracker changeTracker = new DataTreeChangeTracker(defaultAction);
            final DomToNormalizedNodeParserFactory.BuildingStrategyProvider editOperationStrategyProvider =
                    new EditOperationStrategyProvider(changeTracker);

            parseIntoNormalizedNode(schemaNode, element, editOperationStrategyProvider);
            executeOperations(changeTracker);
        }

        return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.absent());
    }

    private void executeOperations(final DataTreeChangeTracker changeTracker) throws DocumentedException {
        final DOMDataReadWriteTransaction rwTx = transactionProvider.getOrCreateTransaction();
        final List<DataTreeChange> aa = changeTracker.getDataTreeChanges();
        final ListIterator<DataTreeChange> iterator = aa.listIterator(aa.size());

        while (iterator.hasPrevious()) {
            final DataTreeChange dtc = iterator.previous();
            executeChange(rwTx, dtc);
        }
    }

    private static void executeChange(final DOMDataReadWriteTransaction rwtx, final DataTreeChange change)
            throws DocumentedException {
        final YangInstanceIdentifier path = YangInstanceIdentifier.create(change.getPath());
        final NormalizedNode<?, ?> changeData = change.getChangeRoot();
        switch (change.getAction()) {
        case NONE:
            return;
        case MERGE:
            mergeParentMap(rwtx, path, changeData);
            rwtx.merge(LogicalDatastoreType.CONFIGURATION, path, changeData);
            break;
        case CREATE:
            try {
                final Optional<NormalizedNode<?, ?>> readResult = rwtx.read(LogicalDatastoreType.CONFIGURATION, path).checkedGet();
                if (readResult.isPresent()) {
                    throw new DocumentedException("Data already exists, cannot execute CREATE operation",
                        ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS, ErrorSeverity.ERROR);
                }
                mergeParentMap(rwtx, path, changeData);
                rwtx.put(LogicalDatastoreType.CONFIGURATION, path, changeData);
            } catch (final ReadFailedException e) {
                LOG.warn("Read from datastore failed when trying to read data for create operation", change, e);
            }
            break;
        case REPLACE:
            mergeParentMap(rwtx, path, changeData);
            rwtx.put(LogicalDatastoreType.CONFIGURATION, path, changeData);
            break;
        case DELETE:
            try {
                final Optional<NormalizedNode<?, ?>> readResult = rwtx.read(LogicalDatastoreType.CONFIGURATION, path).checkedGet();
                if (!readResult.isPresent()) {
                    throw new DocumentedException("Data is missing, cannot execute DELETE operation",
                        ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, ErrorSeverity.ERROR);
                }
                rwtx.delete(LogicalDatastoreType.CONFIGURATION, path);
            } catch (final ReadFailedException e) {
                LOG.warn("Read from datastore failed when trying to read data for delete operation", change, e);
            }
            break;
        case REMOVE:
            rwtx.delete(LogicalDatastoreType.CONFIGURATION, path);
            break;
        default:
            LOG.warn("Unknown/not implemented operation, not executing");
        }
    }

    private static void mergeParentMap(final DOMDataReadWriteTransaction rwtx, final YangInstanceIdentifier path,
                                final NormalizedNode<?, ?> change) {
        if (change instanceof MapEntryNode) {
            final YangInstanceIdentifier mapNodeYid = path.getParent();
            //merge empty map
            final MapNode mixinNode = Builders.mapBuilder()
                    .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(mapNodeYid.getLastPathArgument().getNodeType()))
                    .build();
            rwtx.merge(LogicalDatastoreType.CONFIGURATION, mapNodeYid, mixinNode);
        }
    }

    private NormalizedNode<?, ?> parseIntoNormalizedNode(final DataSchemaNode schemaNode, final XmlElement element,
            final DomToNormalizedNodeParserFactory.BuildingStrategyProvider editOperationStrategyProvider) {
        if (schemaNode instanceof ContainerSchemaNode) {
            return DomToNormalizedNodeParserFactory
                    .getInstance(DomUtils.defaultValueCodecProvider(), schemaContext.getCurrentContext(), editOperationStrategyProvider)
                    .getContainerNodeParser()
                    .parse(Collections.singletonList(element.getDomElement()), (ContainerSchemaNode) schemaNode);
        } else if (schemaNode instanceof ListSchemaNode) {
            return DomToNormalizedNodeParserFactory
                    .getInstance(DomUtils.defaultValueCodecProvider(), schemaContext.getCurrentContext(), editOperationStrategyProvider)
                    .getMapNodeParser()
                    .parse(Collections.singletonList(element.getDomElement()), (ListSchemaNode) schemaNode);
        } else {
            //this should never happen since edit-config on any other node type should not be possible nor makes sense
            LOG.debug("DataNode from module is not ContainerSchemaNode nor ListSchemaNode, aborting..");
        }
        throw new UnsupportedOperationException("implement exception if parse fails");
    }

    private Optional<DataSchemaNode> getSchemaNodeFromNamespace(final String namespace, final XmlElement element)
            throws DocumentedException {
        Optional<DataSchemaNode> dataSchemaNode = Optional.absent();
        try {
            // returns module with newest revision since findModuleByNamespace returns a set of modules and we only
            // need the newest one
            final Module module = schemaContext.getCurrentContext().findModuleByNamespaceAndRevision(new URI(namespace), null);
            if (module == null) {
                // no module is present with this namespace
                throw new NetconfDocumentedException("Unable to find module by namespace: " + namespace,
                        ErrorType.APPLICATION, ErrorTag.UNKNOWN_NAMESPACE, ErrorSeverity.ERROR);
            }
            final DataSchemaNode schemaNode =
                    module.getDataChildByName(QName.create(module.getQNameModule(), element.getName()));
            if (schemaNode != null) {
                dataSchemaNode = Optional.of(schemaNode);
            } else {
                throw new DocumentedException("Unable to find node with namespace: " + namespace + "in module: " + module.toString(),
                        ErrorType.APPLICATION,
                        ErrorTag.UNKNOWN_NAMESPACE,
                        ErrorSeverity.ERROR);
            }
        } catch (final URISyntaxException e) {
            LOG.debug("Unable to create URI for namespace : {}", namespace);
        }

        return dataSchemaNode;
    }

    private static Datastore extractTargetParameter(final XmlElement operationElement) throws DocumentedException {
        final NodeList elementsByTagName = operationElement.getDomElement().getElementsByTagName(TARGET_KEY);
        // Direct lookup instead of using XmlElement class due to performance
        if (elementsByTagName.getLength() == 0) {
            final Map<String, String> errorInfo = ImmutableMap.of("bad-attribute", TARGET_KEY, "bad-element",
                OPERATION_NAME);
            throw new DocumentedException("Missing target element", ErrorType.PROTOCOL, ErrorTag.MISSING_ATTRIBUTE,
                ErrorSeverity.ERROR, errorInfo);
        } else if (elementsByTagName.getLength() > 1) {
            throw new DocumentedException("Multiple target elements", ErrorType.RPC, ErrorTag.UNKNOWN_ATTRIBUTE,
                ErrorSeverity.ERROR);
        } else {
            final XmlElement targetChildNode = XmlElement.fromDomElement((Element) elementsByTagName.item(0)).getOnlyChildElement();
            return Datastore.valueOf(targetChildNode.getName());
        }
    }

    private static ModifyAction getDefaultOperation(final XmlElement operationElement) throws DocumentedException {
        final NodeList elementsByTagName = operationElement.getDomElement().getElementsByTagName(DEFAULT_OPERATION_KEY);
        if (elementsByTagName.getLength() == 0) {
            return ModifyAction.MERGE;
        } else if (elementsByTagName.getLength() > 1) {
            throw new DocumentedException("Multiple " + DEFAULT_OPERATION_KEY + " elements", ErrorType.RPC,
                ErrorTag.UNKNOWN_ATTRIBUTE, ErrorSeverity.ERROR);
        } else {
            return ModifyAction.fromXmlValue(elementsByTagName.item(0).getTextContent());
        }

    }

    private static XmlElement getElement(final XmlElement operationElement, final String elementName)
            throws DocumentedException {
        final Optional<XmlElement> childNode = operationElement.getOnlyChildElementOptionally(elementName);
        if (!childNode.isPresent()) {
            throw new DocumentedException(elementName + " element is missing",
                    ErrorType.PROTOCOL,
                    ErrorTag.MISSING_ELEMENT,
                    ErrorSeverity.ERROR);
        }

        return childNode.get();
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }

}
