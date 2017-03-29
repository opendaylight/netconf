/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.xml.draft04;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.opendaylight.restconfsb.communicator.api.parser.ErrorParser;
import org.opendaylight.restconfsb.communicator.util.RestconfUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev131019.restconf.Restconf;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;

public class RestconfErrorXmlParser implements ErrorParser {

    private static final YangInstanceIdentifier.NodeIdentifier ERROR_TYPE = new YangInstanceIdentifier.NodeIdentifier(QName.create(Restconf.QNAME, "error-type"));
    private static final YangInstanceIdentifier.NodeIdentifier ERROR_TAG = new YangInstanceIdentifier.NodeIdentifier(QName.create(Restconf.QNAME, "error-tag"));
    private static final YangInstanceIdentifier.NodeIdentifier ERROR_MESSAGE = new YangInstanceIdentifier.NodeIdentifier(QName.create(Restconf.QNAME, "error-message"));

    @Override
    public Collection<RpcError> parseErrors(final String stream) {
        final ContainerNode containerNode = RestconfUtil.parseXmlContainer(new ByteArrayInputStream(stream.getBytes()), RestconfUtil.getErrorsSchemaNode());
        final UnkeyedListNode errorList = (UnkeyedListNode) containerNode.getValue().iterator().next();
        final List<RpcError> rpcErrorList = new ArrayList<>();
        for (final UnkeyedListEntryNode error : errorList.getValue()) {
            final String type = (String) error.getChild(ERROR_TYPE).get().getValue();
            final String tag = (String) error.getChild(ERROR_TAG).get().getValue();
            final String message = (String) error.getChild(ERROR_MESSAGE).get().getValue();
            rpcErrorList.add(RpcResultBuilder.newError(mapToRpcErrorType(type), tag, message));
        }
        return rpcErrorList;
    }

    private static RpcError.ErrorType mapToRpcErrorType(final String type) {
        switch (type) {
            case "application":
                return RpcError.ErrorType.APPLICATION;
            case "protocol":
                return RpcError.ErrorType.PROTOCOL;
            case "rpc":
                return RpcError.ErrorType.RPC;
            case "transport":
                return RpcError.ErrorType.TRANSPORT;
            default:
                return RpcError.ErrorType.APPLICATION;
        }
    }
}
