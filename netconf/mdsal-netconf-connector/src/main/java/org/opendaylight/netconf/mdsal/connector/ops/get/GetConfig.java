/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops.get;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.mdsal.connector.ops.Datastore;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class GetConfig extends AbstractGet {

    private static final String OPERATION_NAME = "get-config";

    public GetConfig(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext,
                     final TransactionProvider transactionProvider) {
        super(netconfSessionIdForReporting, schemaContext, transactionProvider);
    }

    @Override
    Datastore getDatastore(final XmlElement operationElement) throws DocumentedException {
        final GetConfigExecution getConfigExecution = GetConfigExecution.fromXml(operationElement, OPERATION_NAME);
        GetConfigExecution.validateInputRpc(operationElement, OPERATION_NAME);
        Preconditions.checkState(getConfigExecution.getDatastore().isPresent());
        return getConfigExecution.getDatastore().get();
    }

    @Override
    CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> read(final DOMDataReadWriteTransaction rwTx,
                                                                            final YangInstanceIdentifier path) {
        final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> read =
                rwTx.read(LogicalDatastoreType.CONFIGURATION, path);
        return read;
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }

    private static final class GetConfigExecution {
        private final Optional<Datastore> datastore;

        GetConfigExecution(final Optional<Datastore> datastore) {
            this.datastore = datastore;
        }

        static GetConfigExecution fromXml(final XmlElement xml, final String operationName) throws DocumentedException {
            try {
                validateInputRpc(xml, operationName);
            } catch (final DocumentedException e) {
                throw new DocumentedException("Incorrect RPC: " + e.getMessage(), e.getErrorType(), e.getErrorTag(),
                        e.getErrorSeverity(), e.getErrorInfo());
            }

            final Optional<Datastore> sourceDatastore;
            try {
                sourceDatastore = parseSource(xml);
            } catch (final DocumentedException e) {
                throw new DocumentedException("Get-config source attribute error: " + e.getMessage(), e.getErrorType(),
                        e.getErrorTag(), e.getErrorSeverity(), e.getErrorInfo());
            }

            return new GetConfigExecution(sourceDatastore);
        }

        private static Optional<Datastore> parseSource(final XmlElement xml) throws DocumentedException {
            final Optional<XmlElement> sourceElement = xml.getOnlyChildElementOptionally(XmlNetconfConstants.SOURCE_KEY,
                    XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0);

            return sourceElement.isPresent()
                    ? Optional.of(Datastore.valueOf(sourceElement.get().getOnlyChildElement().getName()))
                    : Optional.<Datastore>absent();
        }

        private static void validateInputRpc(final XmlElement xml, final String operationName) throws
                DocumentedException {
            xml.checkName(operationName);
            xml.checkNamespace(XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0);
        }

        public Optional<Datastore> getDatastore() {
            return datastore;
        }

    }

}
