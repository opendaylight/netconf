/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema.mapping;

import java.util.Date;
import java.util.Map;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.controller.config.util.xml.MissingNameSpaceException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.sal.connect.api.MessageTransformer;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.MessageCounter;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Transforms anyxml rpcs for schemaless netconf devices.
 */
public class SchemalessMessageTransformer implements MessageTransformer<NetconfMessage> {

    private static final YangInstanceIdentifier.NodeIdentifier REPLY_ID =
            new YangInstanceIdentifier.NodeIdentifier(NetconfMessageTransformUtil.NETCONF_RPC_REPLY_QNAME);
    // TODO maybe we should move this somewhere else as this
    // might be used in applications using schemaless mountpoints
    public static final YangInstanceIdentifier.NodeIdentifier SCHEMALESS_NOTIFICATION_PAYLOAD =
            // FIXME: assign proper namespace
            new YangInstanceIdentifier.NodeIdentifier(QName.create("", "schemaless-notification-payload"));

    private final MessageCounter counter;

    public SchemalessMessageTransformer(final MessageCounter counter) {
        this.counter = counter;
    }

    @Override
    public DOMNotification toNotification(final NetconfMessage message) {
        final Map.Entry<Date, XmlElement> stripped = NetconfMessageTransformUtil.stripNotification(message);
        final QName notificationNoRev;
        try {
            notificationNoRev =
                    QName.create(stripped.getValue().getNamespace(), stripped.getValue().getName()).withoutRevision();
        } catch (final MissingNameSpaceException e) {
            throw new IllegalArgumentException("Unable to parse notification "
                    + message + ", cannot find namespace", e);
        }

        final AnyXmlNode notificationPayload = Builders.anyXmlBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(notificationNoRev))
                .withValue(new DOMSource(stripped.getValue().getDomElement()))
                .build();

        final ContainerNode notificationBody = Builders.containerBuilder()
                .withNodeIdentifier(SCHEMALESS_NOTIFICATION_PAYLOAD)
                .withChild(notificationPayload)
                .build();

        return new NetconfMessageTransformer.NetconfDeviceNotification(notificationBody, stripped.getKey());
    }

    @Override
    public NetconfMessage toRpcRequest(final SchemaPath rpc, final NormalizedNode<?, ?> input) {
        final DOMSource payload = (DOMSource) input.getValue();
        wrapPayload((Document) payload.getNode());
        return new NetconfMessage((Document) ((AnyXmlNode) input).getValue().getNode());
    }

    /**
     * Transforms reply message to anyXml node.
     * In case, that rpc-reply doesn't contain data and contains only &lt;ok/&gt; element, returns null.
     * @param rpcReply reply message
     * @return anyxml
     */
    @Override
    public DOMRpcResult toRpcResult(final NetconfMessage rpcReply, final SchemaPath rpc) {
        final Document document = rpcReply.getDocument();
        final AnyXmlNode result;
        if (BaseRpcSchemalessTransformer.isOkPresent(document)) {
            result =  null;
        } else {
            result = Builders.anyXmlBuilder()
                    .withNodeIdentifier(REPLY_ID)
                    .withValue(new DOMSource(rpcReply.getDocument()))
                    .build();
        }
        return new DefaultDOMRpcResult(result);
    }

    private void wrapPayload(final Document doc) {
        final Element payload = doc.getDocumentElement();
        doc.removeChild(payload);
        final Element rpcNS =
                doc.createElementNS(NetconfMessageTransformUtil.NETCONF_RPC_QNAME.getNamespace().toString(),
                NetconfMessageTransformUtil.NETCONF_RPC_QNAME.getLocalName());
        // set msg id
        rpcNS.setAttribute(NetconfMessageTransformUtil.MESSAGE_ID_ATTR,
                counter.getNewMessageId(NetconfMessageTransformUtil.MESSAGE_ID_PREFIX));
        rpcNS.appendChild(payload);
        doc.appendChild(rpcNS);
    }
}
