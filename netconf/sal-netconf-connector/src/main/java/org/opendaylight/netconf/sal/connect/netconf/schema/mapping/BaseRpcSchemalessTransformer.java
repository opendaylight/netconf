/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema.mapping;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.sal.connect.api.MessageTransformer;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.MessageCounter;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Transforms base netconf RPCs.
 */
public class BaseRpcSchemalessTransformer implements MessageTransformer<NetconfMessage> {
    private final ImmutableMap<QName, ? extends RpcDefinition> mappedRpcs;
    private final EffectiveModelContext modelContext;
    private final MessageCounter counter;

    public BaseRpcSchemalessTransformer(final BaseNetconfSchemas baseSchemas, final MessageCounter counter) {
        final BaseSchema baseSchema = baseSchemas.getBaseSchema();
        mappedRpcs = baseSchema.getMappedRpcs();
        modelContext = baseSchema.getEffectiveModelContext();
        this.counter = counter;
    }

    @Override
    public DOMNotification toNotification(final NetconfMessage message) {
        throw new UnsupportedOperationException("Notifications not supported.");
    }

    @Override
    public NetconfMessage toRpcRequest(final SchemaPath rpc, final NormalizedNode<?, ?> payload) {
        // In case no input for rpc is defined, we can simply construct the payload here
        final QName rpcQName = rpc.getLastComponent();

        final RpcDefinition mappedRpc = Preconditions.checkNotNull(mappedRpcs.get(rpcQName),
            "Unknown rpc %s, available rpcs: %s", rpcQName, mappedRpcs.keySet());
        final DOMResult domResult = NetconfMessageTransformUtil.prepareDomResultForRpcRequest(rpcQName, counter);
        if (mappedRpc.getInput().getChildNodes().isEmpty()) {
            return new NetconfMessage(domResult.getNode().getOwnerDocument());
        }

        Preconditions.checkNotNull(payload, "Transforming an rpc with input: %s, payload cannot be null", rpcQName);
        Preconditions.checkArgument(payload instanceof ContainerNode,
                "Transforming an rpc with input: %s, payload has to be a container, but was: %s", rpcQName, payload);

        // Set the path to the input of rpc for the payload stream writer
        final SchemaPath inputPath = rpc.createChild(YangConstants.operationInputQName(rpcQName.getModule()));
        final DOMResult result = domResult;

        try {
            NetconfMessageTransformUtil.writeNormalizedRpc((ContainerNode) payload, result, inputPath, modelContext);
        } catch (final XMLStreamException | IOException | IllegalStateException e) {
            throw new IllegalStateException("Unable to serialize " + inputPath, e);
        }

        final Document node = result.getNode().getOwnerDocument();

        return new NetconfMessage(node);
    }

    @Override
    public DOMRpcResult toRpcResult(final NetconfMessage message, final SchemaPath rpc) {
        final NormalizedNode<?, ?> normalizedNode;
        final QName rpcQName = rpc.getLastComponent();
        if (NetconfMessageTransformUtil.isDataRetrievalOperation(rpcQName)) {
            final Element xmlData = NetconfMessageTransformUtil.getDataSubtree(message.getDocument());
            final Document data = XmlUtil.newDocument();
            data.appendChild(data.importNode(xmlData, true));
            DOMSourceAnyxmlNode xmlDataNode = Builders.anyXmlBuilder()
                    .withNodeIdentifier(NetconfMessageTransformUtil.NETCONF_DATA_NODEID)
                    .withValue(new DOMSource(data))
                    .build();

            normalizedNode = Builders.containerBuilder()
                    .withNodeIdentifier(NetconfMessageTransformUtil.NETCONF_RPC_REPLY_NODEID)
                    .withChild(xmlDataNode).build();
        } else {
            //other base rpcs don't have any output, we can simply construct the payload here
            Preconditions.checkArgument(isOkPresent(message.getDocument()),
                    "Unexpected content in response of rpc: %s, %s", rpc.getLastComponent(), message);
            normalizedNode = null;

        }
        return new DefaultDOMRpcResult(normalizedNode);
    }

    // FIXME this should go to some util class
    static boolean isOkPresent(final Document doc) {
        return XmlElement.fromDomDocument(doc).getOnlyChildElementWithSameNamespaceOptionally("ok").isPresent();
    }
}
