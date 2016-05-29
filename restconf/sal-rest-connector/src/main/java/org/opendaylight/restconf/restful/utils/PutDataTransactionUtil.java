/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.CheckedFuture;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.md.sal.rest.common.RestconfValidationUtils;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public class PutDataTransactionUtil {

    private static final String NETCONF_BASE = "urn:ietf:params:xml:ns:netconf:base:1.0";
    private static final String NETCONF_BASE_PAYLOAD_NAME = "data";
    private static final QName NETCONF_BASE_QNAME;

    static {
        try {
            final Date date = new SimpleDateFormat("yyyy-MM-dd").parse("2014-07-08");
            NETCONF_BASE_QNAME = QName.create(QNameModule.create(new URI(NETCONF_BASE), null),
                    NETCONF_BASE_PAYLOAD_NAME);
        } catch (final ParseException e) {
            final String errMsg = "It wasn't possible to convert revision date of sal-remote-augment to date";
            throw new RestconfDocumentedException(errMsg, ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED);
        } catch (final URISyntaxException e) {
            final String errMsg = "It wasn't possible to create instance of URI class with " + NETCONF_BASE + " URI";
            throw new RestconfDocumentedException(errMsg, ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED);
        }

    }

    public static void validInputData(final SchemaNode schemaNode, final NormalizedNodeContext payload) {
        if ((schemaNode != null) && (payload.getData() == null)) {
            throw new RestconfDocumentedException("Input is required.", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        } else if ((schemaNode == null) && (payload.getData() != null)) {
            throw new RestconfDocumentedException("No input expected.", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }
    }

    public static void validTopLevelNodeName(final YangInstanceIdentifier yiid, final NormalizedNodeContext payload) {
        final String payloadName = payload.getData().getNodeType().getLocalName();
        if (yiid.isEmpty()) {
            if (!payload.getData().getNodeType().equals(NETCONF_BASE_QNAME)) {
                throw new RestconfDocumentedException("Instance identifier has to contain at least one path argument",
                        ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
            }
        } else {
            final String identifierName = yiid.getLastPathArgument().getNodeType().getLocalName();
            if (!payloadName.equals(identifierName)) {
                throw new RestconfDocumentedException(
                        "Payload name (" + payloadName + ") is different from identifier name (" + identifierName + ")",
                        ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
            }
        }
    }

    /**
     * Validates whether keys in {@code payload} are equal to values of keys in
     * {@code iiWithData} for list schema node
     *
     * @throws RestconfDocumentedException
     *             if key values or key count in payload and URI isn't equal
     *
     */
    public static void validateListKeysEqualityInPayloadAndUri(final NormalizedNodeContext payload) {
        final InstanceIdentifierContext<?> iiWithData = payload.getInstanceIdentifierContext();
        final PathArgument lastPathArgument = iiWithData.getInstanceIdentifier().getLastPathArgument();
        final SchemaNode schemaNode = iiWithData.getSchemaNode();
        final NormalizedNode<?, ?> data = payload.getData();
        if (schemaNode instanceof ListSchemaNode) {
            final List<QName> keyDefinitions = ((ListSchemaNode) schemaNode).getKeyDefinition();
            if ((lastPathArgument instanceof NodeIdentifierWithPredicates) && (data instanceof MapEntryNode)) {
                final Map<QName, Object> uriKeyValues = ((NodeIdentifierWithPredicates) lastPathArgument)
                        .getKeyValues();
                isEqualUriAndPayloadKeyValues(uriKeyValues, (MapEntryNode) data, keyDefinitions);
            }
        }
    }

    private static void isEqualUriAndPayloadKeyValues(final Map<QName, Object> uriKeyValues, final MapEntryNode payload,
            final List<QName> keyDefinitions) {

        final Map<QName, Object> mutableCopyUriKeyValues = Maps.newHashMap(uriKeyValues);
        for (final QName keyDefinition : keyDefinitions) {
            final Object uriKeyValue = mutableCopyUriKeyValues.remove(keyDefinition);
            // should be caught during parsing URI to InstanceIdentifier
            RestconfValidationUtils.checkDocumentedError(uriKeyValue != null, ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                    "Missing key " + keyDefinition + " in URI.");

            final Object dataKeyValue = payload.getIdentifier().getKeyValues().get(keyDefinition);

            if (!uriKeyValue.equals(dataKeyValue)) {
                final String errMsg = "The value '" + uriKeyValue + "' for key '" + keyDefinition.getLocalName()
                        + "' specified in the URI doesn't match the value '" + dataKeyValue
                        + "' specified in the message body. ";
                throw new RestconfDocumentedException(errMsg, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
            }
        }
    }

    public static CheckedFuture<Void, TransactionCommitFailedException> putData(
            final DOMTransactionChain transactionChain, final NormalizedNodeContext payload,
            final SchemaContextRef schemaCtxRef) {
        final DOMDataWriteTransaction writeTx = transactionChain.newWriteOnlyTransaction();
        final YangInstanceIdentifier path = payload.getInstanceIdentifierContext().getInstanceIdentifier();
        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();
        if (mountPoint == null) {
            return submitData(path, schemaCtxRef.get(), writeTx, payload.getData());
        } else {
            final Optional<DOMDataBroker> domDataBrokerService = mountPoint.getService(DOMDataBroker.class);

            if (domDataBrokerService.isPresent()) {
                return submitData(path, mountPoint.getSchemaContext(), writeTx, payload.getData());
            }
            final String errMsg = "DOM data broker service isn't available for mount point " + path;
            throw new RestconfDocumentedException(errMsg);
        }
    }

    private static CheckedFuture<Void, TransactionCommitFailedException> submitData(final YangInstanceIdentifier path,
            final SchemaContext schemaContext,
            final DOMDataWriteTransaction writeTx, final NormalizedNode<?, ?> data) {
        TransactionUtil.ensureParentsByMerge(path, schemaContext, writeTx);
        writeTx.put(LogicalDatastoreType.CONFIGURATION, path, data);
        return writeTx.submit();
    }
}
