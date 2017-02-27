/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.schema.mapping;

import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.IETF_NETCONF_NOTIFICATIONS;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.NETCONF_URI;
import static org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil.toPath;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import org.opendaylight.controller.config.util.xml.MissingNameSpaceException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.md.sal.dom.api.DOMEvent;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.sal.connect.api.MessageTransformer;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.netconf.sal.connect.util.MessageCounter;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XmlUtils;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.parser.DomToNormalizedNodeParserFactory;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class NetconfMessageTransformer implements MessageTransformer<NetconfMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfMessageTransformer.class);

    private static final Function<SchemaNode, QName> QNAME_FUNCTION = new Function<SchemaNode, QName>() {
        @Override
        public QName apply(final SchemaNode rpcDefinition) {
            return rpcDefinition.getQName();
        }
    };

    private static final Function<SchemaNode, QName> QNAME_NOREV_FUNCTION = new Function<SchemaNode, QName>() {
        @Override
        public QName apply(final SchemaNode notification) {
            return QNAME_FUNCTION.apply(notification).withoutRevision();
        }
    };

    private final SchemaContext schemaContext;
    private final BaseSchema baseSchema;
    private final MessageCounter counter;
    private final Map<QName, RpcDefinition> mappedRpcs;
    private final Multimap<QName, NotificationDefinition> mappedNotifications;
    private final DomToNormalizedNodeParserFactory parserFactory;

    public NetconfMessageTransformer(final SchemaContext schemaContext, final boolean strictParsing) {
        this(schemaContext, strictParsing, BaseSchema.BASE_NETCONF_CTX);
    }

    public NetconfMessageTransformer(final SchemaContext schemaContext, final boolean strictParsing, final BaseSchema baseSchema) {
        this.counter = new MessageCounter();
        this.schemaContext = schemaContext;
        parserFactory = DomToNormalizedNodeParserFactory.getInstance(XmlUtils.DEFAULT_XML_CODEC_PROVIDER, schemaContext, strictParsing);
        mappedRpcs = Maps.uniqueIndex(schemaContext.getOperations(), QNAME_FUNCTION);
        mappedNotifications = Multimaps.index(schemaContext.getNotifications(), QNAME_NOREV_FUNCTION);
        this.baseSchema = baseSchema;
    }

    @Override
    public synchronized DOMNotification toNotification(final NetconfMessage message) {
        final Map.Entry<Date, XmlElement> stripped = NetconfMessageTransformUtil.stripNotification(message);
        final QName notificationNoRev;
        try {
            notificationNoRev = QName.create(stripped.getValue().getNamespace(), stripped.getValue().getName()).withoutRevision();
        } catch (final MissingNameSpaceException e) {
            throw new IllegalArgumentException("Unable to parse notification " + message + ", cannot find namespace", e);
        }
        final Collection<NotificationDefinition> notificationDefinitions = mappedNotifications.get(notificationNoRev);
        Preconditions.checkArgument(notificationDefinitions.size() > 0,
                "Unable to parse notification %s, unknown notification. Available notifications: %s", notificationDefinitions, mappedNotifications.keySet());

        final NotificationDefinition mostRecentNotification = getMostRecentNotification(notificationDefinitions);

        final ContainerSchemaNode notificationAsContainerSchemaNode = NetconfMessageTransformUtil.createSchemaForNotification(mostRecentNotification);

        final Element element = stripped.getValue().getDomElement();
        final ContainerNode content;
        try {
            content = parserFactory.getContainerNodeParser().parse(Collections.singleton(element),
                notificationAsContainerSchemaNode);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Failed to parse notification %s", element), e);
        }
        return new NetconfDeviceNotification(content, stripped.getKey());
    }

    private static NotificationDefinition getMostRecentNotification(final Collection<NotificationDefinition> notificationDefinitions) {
        Comparator<NotificationDefinition> cmp = (o1, o2) -> o1.getQName().getRevision().compareTo(o2.getQName().getRevision());

        return Collections.max(notificationDefinitions, cmp);
    }

    @Override
    public NetconfMessage toRpcRequest(SchemaPath rpc, final NormalizedNode<?, ?> payload) {
        // In case no input for rpc is defined, we can simply construct the payload here
        final QName rpcQName = rpc.getLastComponent();
        Map<QName, RpcDefinition> currentMappedRpcs = mappedRpcs;

        // Determine whether a base netconf operation is being invoked and also check if the device exposed model for base netconf
        // If no, use pre built base netconf operations model
        //TODO this might be redundant but needs deeper investigation.
        final boolean needToUseBaseCtx = mappedRpcs.get(rpcQName) == null && isBaseOrNotificationRpc(rpcQName);
        if(needToUseBaseCtx) {
            currentMappedRpcs = baseSchema.getMappedRpcs();
        }

        Preconditions.checkNotNull(currentMappedRpcs.get(rpcQName), "Unknown rpc %s, available rpcs: %s", rpcQName, currentMappedRpcs.keySet());
        if(currentMappedRpcs.get(rpcQName).getInput().getChildNodes().isEmpty()) {
            return new NetconfMessage(NetconfMessageTransformUtil.prepareDomResultForRpcRequest(rpcQName, counter).getNode().getOwnerDocument());
        }

        Preconditions.checkNotNull(payload, "Transforming an rpc with input: %s, payload cannot be null", rpcQName);
        Preconditions.checkArgument(payload instanceof ContainerNode,
                "Transforming an rpc with input: %s, payload has to be a container, but was: %s", rpcQName, payload);

        // Set the path to the input of rpc for the node stream writer
        rpc = rpc.createChild(QName.create(rpcQName, "input").intern());
        final DOMResult result = NetconfMessageTransformUtil.prepareDomResultForRpcRequest(rpcQName, counter);

        try {
            // If the schema context for netconf device does not contain model for base netconf operations, use default pre build context with just the base model
            // This way operations like lock/unlock are supported even if the source for base model was not provided
            SchemaContext ctx = needToUseBaseCtx ? baseSchema.getSchemaContext() : schemaContext;
            NetconfMessageTransformUtil.writeNormalizedRpc(((ContainerNode) payload), result, rpc, ctx);
        } catch (final XMLStreamException | IOException | IllegalStateException e) {
            throw new IllegalStateException("Unable to serialize " + rpc, e);
        }

        final Document node = result.getNode().getOwnerDocument();

        return new NetconfMessage(node);
    }

    private boolean isBaseOrNotificationRpc(final QName rpc) {
        return rpc.getNamespace().equals(NETCONF_URI) ||
                rpc.getNamespace().equals(IETF_NETCONF_NOTIFICATIONS.getNamespace()) ||
                rpc.getNamespace().equals(NetconfMessageTransformUtil.CREATE_SUBSCRIPTION_RPC_QNAME.getNamespace());
    }


    @Override
    public synchronized DOMRpcResult toRpcResult(final NetconfMessage message, final SchemaPath rpc) {
        final NormalizedNode<?, ?> normalizedNode;
        final QName rpcQName = rpc.getLastComponent();
        if (NetconfMessageTransformUtil.isDataRetrievalOperation(rpcQName)) {
            final Element xmlData = NetconfMessageTransformUtil.getDataSubtree(message.getDocument());
            final ContainerSchemaNode schemaForDataRead = NetconfMessageTransformUtil.createSchemaForDataRead(schemaContext);
            final ContainerNode dataNode;

            try {
                dataNode = parserFactory.getContainerNodeParser().parse(Collections.singleton(xmlData), schemaForDataRead);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(String.format("Failed to parse data response %s", xmlData), e);
            }

            normalizedNode = Builders.containerBuilder().withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(NetconfMessageTransformUtil.NETCONF_RPC_REPLY_QNAME))
                    .withChild(dataNode).build();
        } else {

            Map<QName, RpcDefinition> currentMappedRpcs = mappedRpcs;

            // Determine whether a base netconf operation is being invoked and also check if the device exposed model for base netconf
            // If no, use pre built base netconf operations model
            //TODO this might be redundant but needs deeper investigation.
            final boolean needToUseBaseCtx = mappedRpcs.get(rpcQName) == null && isBaseOrNotificationRpc(rpcQName);
            if(needToUseBaseCtx) {
                currentMappedRpcs = baseSchema.getMappedRpcs();
            }

            final RpcDefinition rpcDefinition = currentMappedRpcs.get(rpcQName);
            Preconditions.checkArgument(rpcDefinition != null, "Unable to parse response of %s, the rpc is unknown", rpcQName);

            // In case no input for rpc is defined, we can simply construct the payload here
            if (rpcDefinition.getOutput().getChildNodes().isEmpty()) {
                Preconditions.checkArgument(XmlElement.fromDomDocument(
                    message.getDocument()).getOnlyChildElementWithSameNamespaceOptionally("ok").isPresent(),
                    "Unexpected content in response of rpc: %s, %s", rpcDefinition.getQName(), message);
                normalizedNode = null;
            } else {
                final Element element = message.getDocument().getDocumentElement();
                try {
                    normalizedNode = parserFactory.getContainerNodeParser().parse(Collections.singleton(element),
                        rpcDefinition.getOutput());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(String.format("Failed to parse RPC response %s", element), e);
                }
            }
        }
        return new DefaultDOMRpcResult(normalizedNode);
    }

    static class NetconfDeviceNotification implements DOMNotification, DOMEvent {
        private final ContainerNode content;
        private final SchemaPath schemaPath;
        private final Date eventTime;

        NetconfDeviceNotification(final ContainerNode content, final Date eventTime) {
            this.content = content;
            this.eventTime = eventTime;
            this.schemaPath = toPath(content.getNodeType());
        }

        @Nonnull
        @Override
        public SchemaPath getType() {
            return schemaPath;

        }

        @Nonnull
        @Override
        public ContainerNode getBody() {
            return content;
        }

        @Override
        public Date getEventTime() {
            return eventTime;
        }
    }
}
