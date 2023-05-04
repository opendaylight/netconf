/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import java.time.Instant;
import java.util.Map;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.MissingNameSpaceException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.client.mdsal.api.NotificationTransformer;
import org.opendaylight.netconf.client.mdsal.api.RpcTransformer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DOMSourceAnyxmlNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Transforms anyxml rpcs for schemaless netconf devices.
 */
public class SchemalessMessageTransformer implements NotificationTransformer, RpcTransformer<DOMSource, DOMSource> {
    // TODO maybe we should move this somewhere else as this
    // might be used in applications using schemaless mountpoints
    public static final NodeIdentifier SCHEMALESS_NOTIFICATION_PAYLOAD =
            // FIXME: assign proper namespace
            new NodeIdentifier(QName.create("", "schemaless-notification-payload"));

    private final MessageCounter counter;

    public SchemalessMessageTransformer(final MessageCounter counter) {
        this.counter = counter;
    }

    @Override
    public DOMNotification toNotification(final NetconfMessage message) {
        final Map.Entry<Instant, XmlElement> stripped = NetconfMessageTransformUtil.stripNotification(message);
        final QName notificationNoRev;
        try {
            notificationNoRev =
                    QName.create(stripped.getValue().getNamespace(), stripped.getValue().getName()).withoutRevision();
        } catch (final MissingNameSpaceException e) {
            throw new IllegalArgumentException("Unable to parse notification "
                    + message + ", cannot find namespace", e);
        }

        final DOMSourceAnyxmlNode notificationPayload = Builders.anyXmlBuilder()
                .withNodeIdentifier(new NodeIdentifier(notificationNoRev))
                .withValue(new DOMSource(stripped.getValue().getDomElement()))
                .build();

        final ContainerNode notificationBody = Builders.containerBuilder()
                .withNodeIdentifier(SCHEMALESS_NOTIFICATION_PAYLOAD)
                .withChild(notificationPayload)
                .build();

        return new NetconfMessageTransformer.NetconfDeviceNotification(notificationBody, stripped.getKey());
    }

    @Override
    public NetconfMessage toRpcRequest(final QName rpc, final DOMSource payload) {
        wrapPayload((Document) payload.getNode());
        return new NetconfMessage((Document) payload.getNode());
    }

    /**
     * Transforms reply message to anyXml node.
     * In case, that rpc-reply doesn't contain data and contains only &lt;ok/&gt; element, returns null.
     * @param resultPayload reply message
     * @return anyxml
     */
    @Override
    public DOMSource toRpcResult(final RpcResult<NetconfMessage> resultPayload, final QName rpc) {
        final var document = resultPayload.getResult().getDocument();
        return BaseRpcSchemalessTransformer.isOkPresent(document) ? null : new DOMSource(document);
    }

    private void wrapPayload(final Document doc) {
        final Element payload = doc.getDocumentElement();
        doc.removeChild(payload);
        final Element rpcNS =
                doc.createElementNS(NetconfMessageTransformUtil.NETCONF_RPC_QNAME.getNamespace().toString(),
                NetconfMessageTransformUtil.NETCONF_RPC_QNAME.getLocalName());
        // set msg id
        rpcNS.setAttribute(XmlNetconfConstants.MESSAGE_ID,
                counter.getNewMessageId(NetconfMessageTransformUtil.MESSAGE_ID_PREFIX));
        rpcNS.appendChild(payload);
        doc.appendChild(rpcNS);
    }
}
