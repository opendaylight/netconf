/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import java.util.List;
import java.util.ListIterator;
import org.opendaylight.controller.config.facade.xml.TestOption;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorSeverity;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorTag;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorType;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.mdsal.connector.ops.DataTreeChangeTracker.DataTreeChange;
import org.opendaylight.netconf.mdsal.connector.ops.parser.MdsalNetconfParameter;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class EditConfig extends ValidateNetconfOperation {

    private static final Logger LOG = LoggerFactory.getLogger(EditConfig.class);

    private static final String OPERATION_NAME = "edit-config";
    private static final String TEST_OPTION_KEY = "test-option";
    private static final String CONFIG_KEY = "config";
    private static final String DEFAULT_OPERATION_KEY = "default-operation";
    private final TransactionProvider transactionProvider;

    public EditConfig(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext, final TransactionProvider transactionProvider) {
        super(netconfSessionIdForReporting, schemaContext);
        this.transactionProvider = transactionProvider;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement) throws DocumentedException {
        final MdsalNetconfParameter inputParameter = extractTargetParameter(operationElement);
        final ModifyAction defaultAction = getDefaultOperation(operationElement);
        final TestOption testOption = extractTestOption(operationElement);
        final XmlElement configElement = getElement(operationElement, CONFIG_KEY);
        DOMDataReadWriteTransaction rwTx = createReadWriteTransaction(inputParameter.getDatastore());

        for (final XmlElement element : configElement.getChildElements()) {
            final DataTreeChangeTracker changeTracker = createDataTreeChecker(defaultAction, element);
            if (testOption != TestOption.testOnly) {
                executeOperations(changeTracker, rwTx);
            }
        }

        if (inputParameter.getDatastore() == Datastore.running && testOption != TestOption.testOnly) {
            boolean commitStatus = transactionProvider.commitRunningTransaction(rwTx);
            LOG.trace("Commit completed successfully {}", commitStatus);
            rwTx = null;
        }

        return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.<String>absent());
    }

    private DOMDataReadWriteTransaction createReadWriteTransaction(Datastore datastore) {
        return Datastore.candidate == datastore ? transactionProvider.getOrCreateTransaction() : transactionProvider.createRunningTransaction();
    }

    /**
     * Extract value of test-option.
     * @param operationElement
     * @return
     * @throws DocumentedException
     */
    public static TestOption extractTestOption(XmlElement operationElement) throws DocumentedException {
        Optional<XmlElement> testOptionElementOpt = operationElement.getOnlyChildElementWithSameNamespaceOptionally(TEST_OPTION_KEY);
        if (testOptionElementOpt.isPresent()) {
            String testOptionValue = testOptionElementOpt.get().getTextContent();
            return TestOption.getFromXmlName(testOptionValue);
        }
        return TestOption.getDefault();
    }

    private void executeOperations(final DataTreeChangeTracker changeTracker, DOMDataReadWriteTransaction rwTx) throws DocumentedException {
        final List<DataTreeChange> aa = changeTracker.getDataTreeChanges();
        final ListIterator<DataTreeChange> iterator = aa.listIterator(aa.size());

        while (iterator.hasPrevious()) {
            final DataTreeChange dtc = iterator.previous();
            executeChange(rwTx, dtc);
        }
    }

    private void executeChange(final DOMDataReadWriteTransaction rwtx, final DataTreeChange change) throws DocumentedException {
        final YangInstanceIdentifier path = YangInstanceIdentifier.create(change.getPath());
        final NormalizedNode<?, ?> changeData = change.getChangeRoot();
        switch (change.getAction()) {
        case NONE:
            return;
        case MERGE:
            rwtx.merge(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.create(change.getPath()), change.getChangeRoot());
            mergeParentMap(rwtx, path, changeData);
            break;
        case CREATE:
            try {
                final Optional<NormalizedNode<?, ?>> readResult = rwtx.read(LogicalDatastoreType.CONFIGURATION, path).checkedGet();
                if (readResult.isPresent()) {
                    throw new DocumentedException("Data already exists, cannot execute CREATE operation", ErrorType.protocol, ErrorTag.data_exists, ErrorSeverity.error);
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
                    throw new DocumentedException("Data is missing, cannot execute DELETE operation", ErrorType.protocol, ErrorTag.data_missing, ErrorSeverity.error);
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

    private void mergeParentMap(final DOMDataReadWriteTransaction rwtx, final YangInstanceIdentifier path,
                                final NormalizedNode change) {
        if (change instanceof MapEntryNode) {
            final YangInstanceIdentifier mapNodeYid = path.getParent();
            //merge empty map
            final MapNode mixinNode = Builders.mapBuilder()
                    .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(mapNodeYid.getLastPathArgument().getNodeType()))
                    .build();
            rwtx.merge(LogicalDatastoreType.CONFIGURATION, mapNodeYid, mixinNode);
        }
    }

    private ModifyAction getDefaultOperation(final XmlElement operationElement) throws DocumentedException {
        final NodeList elementsByTagName = operationElement.getDomElement().getElementsByTagName(DEFAULT_OPERATION_KEY);
        if(elementsByTagName.getLength() == 0) {
            return ModifyAction.MERGE;
        } else if(elementsByTagName.getLength() > 1) {
            throw new DocumentedException("Multiple " + DEFAULT_OPERATION_KEY + " elements",
                    ErrorType.rpc, ErrorTag.unknown_attribute, ErrorSeverity.error);
        } else {
            return ModifyAction.fromXmlValue(elementsByTagName.item(0).getTextContent());
        }

    }

    private XmlElement getElement(final XmlElement operationElement, final String elementName) throws DocumentedException {
        final Optional<XmlElement> childNode = operationElement.getOnlyChildElementOptionally(elementName);
        if (!childNode.isPresent()) {
            throw new DocumentedException(elementName + " element is missing",
                    ErrorType.protocol,
                    ErrorTag.missing_element,
                    ErrorSeverity.error);
        }

        return childNode.get();
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }

}