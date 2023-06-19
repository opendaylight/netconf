/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteOperations;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.server.mdsal.CurrentSchemaContext;
import org.opendaylight.netconf.server.mdsal.TransactionProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class EditConfig extends AbstractEdit {
    private static final Logger LOG = LoggerFactory.getLogger(EditConfig.class);
    private static final String OPERATION_NAME = "edit-config";
    private static final String DEFAULT_OPERATION = "default-operation";

    private final TransactionProvider transactionProvider;

    public EditConfig(final SessionIdType sessionId, final CurrentSchemaContext schemaContext,
            final TransactionProvider transactionProvider) {
        super(sessionId, schemaContext);
        this.transactionProvider = requireNonNull(transactionProvider);
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
            throws DocumentedException {
        if (Datastore.valueOf(extractTargetElement(operationElement, OPERATION_NAME).getName()) == Datastore.running) {
            throw new DocumentedException("edit-config on running datastore is not supported",
                    ErrorType.PROTOCOL, ErrorTag.OPERATION_NOT_SUPPORTED, ErrorSeverity.ERROR);
        }

        final var defaultAction = getDefaultOperation(operationElement);
        for (var element : getConfigElement(operationElement).getChildElements()) {
            final var writer = new SplittingNormalizedNodeMetadataStreamWriter(defaultAction);
            parseIntoNormalizedNode(getSchemaNodeFromNamespace(element.getNamespace(), element), element, writer);
            executeOperations(writer.getDataTreeChanges());
        }

        return document.createElement(XmlNetconfConstants.OK);
    }

    private void executeOperations(final List<DataTreeChange> changes) throws DocumentedException {
        final var rwTx = transactionProvider.getOrCreateTransaction();
        final var iterator = changes.listIterator(changes.size());
        while (iterator.hasPrevious()) {
            executeChange(rwTx, iterator.previous());
        }
    }

    // FIXME: we should have proper ReadWriteOperations
    private void executeChange(final DOMDataTreeReadWriteTransaction rwtx, final DataTreeChange change)
            throws DocumentedException {
        final var path = change.getPath();
        final var changeData = change.getChangeRoot();
        switch (change.getAction()) {
            case NONE:
                return;
            case MERGE:
                mergeParentMixin(rwtx, path, changeData);
                rwtx.merge(LogicalDatastoreType.CONFIGURATION, path, changeData);
                break;
            case CREATE:
                try {
                    // FIXME: synchronous operation: can we get a rwTx.create() with a per-operation result instead?
                    if (rwtx.exists(LogicalDatastoreType.CONFIGURATION, path).get()) {
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
                    // FIXME: synchronous operation: can we get a rwTx.delete() semantics with a per-operation result
                    //        instead?
                    if (!rwtx.exists(LogicalDatastoreType.CONFIGURATION, path).get()) {
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

    private void mergeParentMixin(final DOMDataTreeWriteOperations rwtx, final YangInstanceIdentifier path,
                                  final NormalizedNode change) {
        final var parentNodeYid = path.getParent();
        if (change instanceof MapEntryNode) {
            final var dataSchemaNode = DataSchemaContextTree.from(schemaContext.getCurrentContext())
                .findChild(parentNodeYid)
                .orElseThrow(() -> new IllegalStateException("Cannot find schema for " + parentNodeYid))
                .dataSchemaNode();

            // we should have the schema node that points to the parent list now, enforce it
            if (!(dataSchemaNode instanceof ListSchemaNode listSchemaNode)) {
                throw new IllegalStateException("Schema node is not pointing to a list");
            }

            // merge empty ordered or unordered map
            rwtx.merge(LogicalDatastoreType.CONFIGURATION, parentNodeYid,
                (listSchemaNode.isUserOrdered() ? Builders.orderedMapBuilder() : Builders.mapBuilder())
                    .withNodeIdentifier(new NodeIdentifier(parentNodeYid.getLastPathArgument().getNodeType()))
                    .build());
        }
    }

    private static EffectiveOperation getDefaultOperation(final XmlElement operationElement)
            throws DocumentedException {
        final var elementsByTagName = getElementsByTagName(operationElement, DEFAULT_OPERATION);
        return switch (elementsByTagName.getLength()) {
            case 0 -> EffectiveOperation.MERGE;
            case 1 ->  EffectiveOperation.ofXmlValue(elementsByTagName.item(0).getTextContent());
            default -> throw new DocumentedException("Multiple " + DEFAULT_OPERATION + " elements", ErrorType.RPC,
                ErrorTag.UNKNOWN_ATTRIBUTE, ErrorSeverity.ERROR);
        };
    }
}
