/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notification.testtool.sb;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.md.sal.common.api.data.AsyncTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChain;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.notification.store.rev160506.Notifications;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.notification.store.rev160506.notifications.Device;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.notification.store.rev160506.notifications.device.Notification;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyXmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Instance of this class listens on notifications from given mount point and writes them to operational datastore node
 * /notification-store:notifications/device[device-id]/notification
 */
class DeviceNotificationListener implements DOMNotificationListener, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceNotificationListener.class);
    private static final XMLOutputFactory XML_FACTORY;

    static {
        XML_FACTORY = XMLOutputFactory.newFactory();
        XML_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    private final DOMTransactionChain txChain;
    private final SchemaContext schemaContext;
    private final String deviceId;
    private final DOMMountPoint mountPoint;
    private final YangInstanceIdentifier.NodeIdentifierWithPredicates deviceEntryId;
    private long notificationCounter;
    private ListenerRegistration<DeviceNotificationListener> listenerRegistration;

    private static final TransactionChainListener listener = new TransactionChainListener() {
        @Override
        public void onTransactionChainFailed(final TransactionChain<?, ?> transactionChain,
                                             final AsyncTransaction<?, ?> asyncTransaction,
                                             final Throwable throwable) {
            LOG.warn("Transaction failed");
        }

        @Override
        public void onTransactionChainSuccessful(final TransactionChain<?, ?> transactionChain) {
            LOG.info("Transaction successful");
        }
    };

    public DeviceNotificationListener(final String deviceId, final DOMDataBroker dataBroker, final DOMMountPoint mountPoint) {
        this.txChain = dataBroker.createTransactionChain(listener);
        this.schemaContext = mountPoint.getSchemaContext();
        this.deviceId = deviceId;
        this.mountPoint = mountPoint;
        this.deviceEntryId = new YangInstanceIdentifier.NodeIdentifierWithPredicates(Device.QNAME, QName.create(Device.QNAME, "device-id"), deviceId);
    }

    public synchronized void registerToAllNotifications() {
        final DOMDataWriteTransaction writeTransaction = txChain.newWriteOnlyTransaction();
        final YangInstanceIdentifier devId = YangInstanceIdentifier.builder()
                .node(Notifications.QNAME)
                .node(Device.QNAME)
                .build();
        final MapNode notificationList = Builders.mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(Notification.QNAME))
                .build();
        final MapEntryNode deviceEntry = Builders.mapEntryBuilder()
                .withNodeIdentifier(deviceEntryId)
                .withChild(notificationList)
                .build();
        final MapNode deviceList = Builders.mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(Device.QNAME))
                .withChild(deviceEntry)
                .build();
        writeTransaction.merge(LogicalDatastoreType.OPERATIONAL, devId, deviceList);
        try {
            writeTransaction.submit().checkedGet();
        } catch (final TransactionCommitFailedException e) {
            LOG.error("failed to register notifications", e);
        }
        final Set<NotificationDefinition> notifications = schemaContext.getNotifications();
        final Collection<SchemaPath> schemaPaths = Collections2.transform(notifications, new Function<NotificationDefinition, SchemaPath>() {
            @Nullable
            @Override
            public SchemaPath apply(@Nullable final NotificationDefinition input) {
                return input.getPath();
            }
        });
        LOG.info("Registering to notifications {} from device {}", schemaPaths, deviceId);
        listenerRegistration = mountPoint.getService(DOMNotificationService.class).get().registerNotificationListener(this, schemaPaths);
    }

    @Override
    public synchronized void onNotification(@Nonnull final DOMNotification notification) {
        try {
            notificationCounter++;
            final String content = writeViaNormalizedNodeWriter(notification.getBody(), notification.getType());
            final Element contentXml = XmlUtil.readXmlToElement(content);
            final DOMDataReadWriteTransaction rw = txChain.newReadWriteTransaction();
            final YangInstanceIdentifier.InstanceIdentifierBuilder builder = YangInstanceIdentifier.builder()
                    .node(Notifications.QNAME)
                    .node(Device.QNAME)
                    .node(deviceEntryId);
            final YangInstanceIdentifier devId = builder.build();
            if (!rw.exists(LogicalDatastoreType.OPERATIONAL, devId).checkedGet()) {
                rw.put(LogicalDatastoreType.OPERATIONAL, devId, createDeviceEntry());
            }
            final MapEntryNode notificationRecord = createNotificationRecord(notificationCounter, contentXml);
            final YangInstanceIdentifier yid = builder
                    .node(Notification.QNAME)
                    .node(notificationRecord.getIdentifier())
                    .build();
            rw.put(LogicalDatastoreType.OPERATIONAL, yid, notificationRecord);
            rw.submit().checkedGet();
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MapEntryNode createDeviceEntry() {
        final MapNode notificationsList = Builders.mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(Notification.QNAME))
                .build();
        return Builders.mapEntryBuilder()
                .withNodeIdentifier(deviceEntryId)
                .withChild(notificationsList)
                .build();
    }

    private MapEntryNode createNotificationRecord(final long id, final Element content) {
        final QName contentQName = QName.create(Notification.QNAME, "content");
        final Document document = XmlUtil.newDocument();
        final Element contentElement = document.createElementNS(contentQName.getNamespace().toString(), contentQName.getLocalName());
        document.appendChild(contentElement);
        final Node notification = document.importNode(content, true);
        contentElement.appendChild(notification);
        final QName idQName = QName.create(Notification.QNAME, "id");
        final LeafNode<Object> idLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(idQName))
                .withValue(id)
                .build();
        final AnyXmlNode contentAnyxml = Builders.anyXmlBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(contentQName))
                .withValue(new DOMSource(document.getDocumentElement()))
                .build();
        return Builders.mapEntryBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifierWithPredicates(Notification.QNAME, idQName, this.notificationCounter))
                .withChild(idLeaf)
                .withChild(contentAnyxml)
                .build();
    }

    private String writeViaNormalizedNodeWriter(final NormalizedNode<?, ?> body, final SchemaPath schemaPath) throws IOException, XMLStreamException {
        final StringWriter result = new StringWriter();
        final XMLStreamWriter xmlWriter = XML_FACTORY.createXMLStreamWriter(result);
        try (final NormalizedNodeStreamWriter normalizedNodeStreamWriter =
                     XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, schemaContext, schemaPath);
             final NormalizedNodeWriter normalizedNodeWriter = NormalizedNodeWriter.forStreamWriter(normalizedNodeStreamWriter)) {
            normalizedNodeWriter.write(body);
            normalizedNodeWriter.flush();
        } finally {
            if (xmlWriter != null) {
                xmlWriter.close();
            }
        }
        //NormalizedNodeStreamWriter doesn't close Notification nodes
        result.append("</").append(body.getIdentifier().getNodeType().getLocalName()).append(">");
        return result.toString();
    }

    @Override
    public void close() throws Exception {
        listenerRegistration.close();
    }
}
