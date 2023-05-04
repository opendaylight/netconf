/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.RpcTransformer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Transforms base netconf RPCs.
 */
public class BaseRpcSchemalessTransformer implements RpcTransformer<NormalizedNode, DOMRpcResult> {
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
    public NetconfMessage toRpcRequest(final QName rpc, final NormalizedNode payload) {
        // In case no input for rpc is defined, we can simply construct the payload here

        final var mappedRpc = checkNotNull(mappedRpcs.get(rpc),
            "Unknown rpc %s, available rpcs: %s", rpc, mappedRpcs.keySet());
        final DOMResult domResult = NetconfMessageTransformUtil.prepareDomResultForRpcRequest(rpc, counter);
        if (mappedRpc.getInput().getChildNodes().isEmpty()) {
            return new NetconfMessage(domResult.getNode().getOwnerDocument());
        }

        checkNotNull(payload, "Transforming an rpc with input: %s, payload cannot be null", rpc);
        checkArgument(payload instanceof ContainerNode,
                "Transforming an rpc with input: %s, payload has to be a container, but was: %s", rpc, payload);

        final DOMResult result = domResult;
        try {
            NetconfMessageTransformUtil.writeNormalizedOperationInput((ContainerNode) payload, result, Absolute.of(rpc),
                modelContext);
        } catch (final XMLStreamException | IOException | IllegalStateException e) {
            throw new IllegalStateException("Unable to serialize input of " + rpc, e);
        }

        final Document node = result.getNode().getOwnerDocument();

        return new NetconfMessage(node);
    }

    @Override
    public DOMRpcResult toRpcResult(final RpcResult<NetconfMessage> resultPayload, final QName rpc) {
        if (!resultPayload.isSuccessful()) {
            return new DefaultDOMRpcResult(resultPayload.getErrors());
        }

        final var message = resultPayload.getResult();
        final ContainerNode normalizedNode;
        if (NetconfMessageTransformUtil.isDataRetrievalOperation(rpc)) {
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
            checkArgument(isOkPresent(message.getDocument()),
                "Unexpected content in response of rpc: %s, %s", rpc, message);
            normalizedNode = null;

        }
        return new DefaultDOMRpcResult(normalizedNode);
    }

    // FIXME this should go to some util class
    static boolean isOkPresent(final Document doc) {
        return XmlElement.fromDomDocument(doc).getOnlyChildElementWithSameNamespaceOptionally("ok").isPresent();
    }
}
