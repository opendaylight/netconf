/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.DocumentedException.ErrorSeverity;
import org.opendaylight.netconf.api.DocumentedException.ErrorTag;
import org.opendaylight.netconf.api.DocumentedException.ErrorType;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class EditConfig extends AbstractEdit {

    private static final Logger LOG = LoggerFactory.getLogger(EditConfig.class);

    private static final String OPERATION_NAME = "edit-config";
    private static final String DEFAULT_OPERATION_KEY = "default-operation";
    private final TransactionProvider transactionProvider;

    public EditConfig(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext,
            final TransactionProvider transactionProvider) {
        super(netconfSessionIdForReporting, schemaContext);
        this.transactionProvider = transactionProvider;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
            throws DocumentedException {
        final XmlElement targetElement = extractTargetElement(operationElement, OPERATION_NAME);
        final Datastore targetDatastore = Datastore.valueOf(targetElement.getName());
        if (targetDatastore == Datastore.running) {
            throw new DocumentedException("edit-config on running datastore is not supported",
                    ErrorType.PROTOCOL,
                    ErrorTag.OPERATION_NOT_SUPPORTED,
                    ErrorSeverity.ERROR);
        }

        final ModifyAction defaultAction = getDefaultOperation(operationElement);

        final XmlElement configElement = getConfigElement(operationElement);

        for (final XmlElement element : configElement.getChildElements()) {
            final String ns = element.getNamespace();
            final DataSchemaNode schemaNode = getSchemaNodeFromNamespace(ns, element);

            final SplittingNormalizedNodeMetadataStreamWriter writer = new SplittingNormalizedNodeMetadataStreamWriter(
                defaultAction);
            parseIntoNormalizedNode(schemaNode, element, writer);
            executeOperations(writer.getDataTreeChanges());
        }

        return document.createElement(XmlNetconfConstants.OK);
    }

    private void executeOperations(final List<DataTreeChange> changes) throws DocumentedException {
        final DOMDataTreeReadWriteTransaction rwTx = transactionProvider.getOrCreateTransaction();
        final ListIterator<DataTreeChange> iterator = changes.listIterator(changes.size());

        while (iterator.hasPrevious()) {
            final DataTreeChange dtc = iterator.previous();
            executeChange(rwTx, dtc);
        }
    }

    private void executeChange(final DOMDataTreeReadWriteTransaction rwtx, final DataTreeChange change)
            throws DocumentedException {
        final YangInstanceIdentifier path = change.getPath();
        final NormalizedNode<?, ?> changeData = change.getChangeRoot();
        switch (change.getAction()) {
            case NONE:
                return;
            case MERGE:
                mergeParentMixin(rwtx, path, changeData);
                rwtx.merge(LogicalDatastoreType.CONFIGURATION, path, changeData);
                break;
            case CREATE:
                try {
                    if (rwtx.read(LogicalDatastoreType.CONFIGURATION, path).get().isPresent()) {
                        throw new DocumentedException("Data already exists, cannot execute CREATE operation",
                            ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS, ErrorSeverity.ERROR);
                    }
                    mergeParentMixin(rwtx, path, changeData);
                    rwtx.put(LogicalDatastoreType.CONFIGURATION, path, changeData);
                } catch (final InterruptedException | ExecutionException e) {
                    LOG.warn("Read from datastore failed when trying to read data for create operation {}", change, e);
                }
                break;
            case REPLACE:
                mergeParentMixin(rwtx, path, changeData);
                rwtx.put(LogicalDatastoreType.CONFIGURATION, path, changeData);
                break;
            case DELETE:
                try {
                    if (rwtx.read(LogicalDatastoreType.CONFIGURATION, path).get().isEmpty()) {
                        throw new DocumentedException("Data is missing, cannot execute DELETE operation",
                            ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, ErrorSeverity.ERROR);
                    }
                    rwtx.delete(LogicalDatastoreType.CONFIGURATION, path);
                } catch (final InterruptedException | ExecutionException e) {
                    LOG.warn("Read from datastore failed when trying to read data for delete operation {}", change, e);
                }
                break;
            case REMOVE:
                rwtx.delete(LogicalDatastoreType.CONFIGURATION, path);
                break;
            default:
                LOG.warn("Unknown/not implemented operation, not executing");
        }
    }

    private void mergeParentMixin(final DOMDataTreeReadWriteTransaction rwtx, final YangInstanceIdentifier path,
                                final NormalizedNode<?, ?> change) {
        final YangInstanceIdentifier parentNodeYid = path.getParent();
        if (change instanceof MapEntryNode) {
            final SchemaNode schemaNode = SchemaContextUtil.findNodeInSchemaContext(
                    schemaContext.getCurrentContext(),
                    parentNodeYid.getPathArguments().stream()
                            // filter out identifiers not present in the schema tree
                            .filter(arg -> !(arg instanceof YangInstanceIdentifier.NodeIdentifierWithPredicates))
                            .filter(arg -> !(arg instanceof YangInstanceIdentifier.AugmentationIdentifier))
                            .map(YangInstanceIdentifier.PathArgument::getNodeType).collect(Collectors.toList()));

            // we should have the schema node that points to the parent list now, enforce it
            Preconditions.checkState(schemaNode instanceof ListSchemaNode, "Schema node is not pointing to a list.");

            //merge empty ordered or unordered map
            if (((ListSchemaNode) schemaNode).isUserOrdered()) {
                final MapNode mixinNode = Builders.orderedMapBuilder()
                        .withNodeIdentifier(
                                new YangInstanceIdentifier.NodeIdentifier(
                                        parentNodeYid.getLastPathArgument().getNodeType()))
                        .build();
                rwtx.merge(LogicalDatastoreType.CONFIGURATION, parentNodeYid, mixinNode);
                return;
            }

            final MapNode mixinNode = Builders.mapBuilder()
                    .withNodeIdentifier(
                            new YangInstanceIdentifier.NodeIdentifier(
                                        parentNodeYid.getLastPathArgument().getNodeType()))
                    .build();
            rwtx.merge(LogicalDatastoreType.CONFIGURATION, parentNodeYid, mixinNode);
        } else if (parentNodeYid.getLastPathArgument() instanceof YangInstanceIdentifier.AugmentationIdentifier) {
            // merge empty augmentation node
            final YangInstanceIdentifier.AugmentationIdentifier augmentationYid =
                (YangInstanceIdentifier.AugmentationIdentifier) parentNodeYid.getLastPathArgument();
            final AugmentationNode augmentationNode = Builders.augmentationBuilder()
                .withNodeIdentifier(augmentationYid).build();
            rwtx.merge(LogicalDatastoreType.CONFIGURATION, parentNodeYid, augmentationNode);
        }
    }

    private static ModifyAction getDefaultOperation(final XmlElement operationElement) throws DocumentedException {
        final NodeList elementsByTagName = getElementsByTagName(operationElement, DEFAULT_OPERATION_KEY);
        if (elementsByTagName.getLength() == 0) {
            return ModifyAction.MERGE;
        } else if (elementsByTagName.getLength() > 1) {
            throw new DocumentedException("Multiple " + DEFAULT_OPERATION_KEY + " elements", ErrorType.RPC,
                ErrorTag.UNKNOWN_ATTRIBUTE, ErrorSeverity.ERROR);
        } else {
            return ModifyAction.fromXmlValue(elementsByTagName.item(0).getTextContent());
        }

    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }
}
