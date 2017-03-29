/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.xml.draft04;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfMessageTransformUtil;
import org.opendaylight.restconfsb.communicator.api.parser.Parser;
import org.opendaylight.restconfsb.communicator.impl.common.RestconfDeviceNotification;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.DomUtils;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.parser.DomToNormalizedNodeParserFactory;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class XmlParser implements Parser {

    private static final String MALFORMED_REPLY = "Malformed reply received";
    private final SchemaContext schemaContext;

    public XmlParser(final SchemaContext schemacontext) {
        this.schemaContext = schemacontext;
    }

    private static RpcDefinition findRpcDefinition(final Module m, final String s) {
        for (final RpcDefinition rpcDef : m.getRpcs()) {
            if (rpcDef.getQName().getNamespace().equals(m.getNamespace())
                    && rpcDef.getQName().getLocalName().equals(s)) {
                return rpcDef;
            }
        }
        throw new IllegalStateException("RPC definition not found");
    }


    @Override
    public NormalizedNode<?, ?> parse(final YangInstanceIdentifier path, final InputStream body) {
        final XmlElement element = getElementFromXml(body, true);
        if (element == null) {
            return null;
        }

        final DataSchemaNode schemaNode = DataSchemaContextTree.from(schemaContext).getChild(path).getDataSchemaNode();

        final DomToNormalizedNodeParserFactory parserFactory = DomToNormalizedNodeParserFactory
                .getInstance(DomUtils.defaultValueCodecProvider(), schemaContext);

        if (schemaNode instanceof ContainerSchemaNode) {
            return parserFactory.getContainerNodeParser()
                    .parse(Collections.singletonList(element.getDomElement()), (ContainerSchemaNode) schemaNode);
        } else if (schemaNode instanceof ListSchemaNode) {
            return parserFactory.getMapNodeParser()
                    .parse(Collections.singletonList(element.getDomElement()), (ListSchemaNode) schemaNode);
        } else if (schemaNode instanceof LeafListSchemaNode) {
            return parserFactory.getLeafSetNodeParser()
                    .parse(Collections.singletonList(element.getDomElement()), (LeafListSchemaNode) schemaNode);
        } else if (schemaNode instanceof LeafSchemaNode) {
            return parserFactory.getLeafNodeParser()
                    .parse(Collections.singletonList(element.getDomElement()), (LeafSchemaNode) schemaNode);
        } else {
            throw new UnsupportedOperationException("Only list, container, leaf and leaflist supported");
        }
    }

    private static XmlElement getElementFromXml(final InputStream body, final boolean checkBody) {
        final Document doc;

        try {
            if (checkBody && body.available() == 0) {
                return null;
            }
            doc = XmlUtil.readXmlToDocument(body);
        } catch (IOException | SAXException e) {
            throw new IllegalStateException(MALFORMED_REPLY, e);
        }

        return XmlElement.fromDomDocument(doc);
    }

    @Nullable
    @Override
    public NormalizedNode<?, ?> parseRpcOutput(final SchemaPath path, final InputStream body) {
        final Module module = schemaContext.findModuleByNamespaceAndRevision(path.getLastComponent().getNamespace(), path.getLastComponent().getRevision());
        final RpcDefinition definition = findRpcDefinition(module, path.getLastComponent().getLocalName());
        if (definition.getOutput() != null) {
            final XmlElement element = getElementFromXml(body, false);
                final ContainerSchemaNode schemaNode = definition.getOutput();
                return DomToNormalizedNodeParserFactory
                        .getInstance(DomUtils.defaultValueCodecProvider(), schemaContext)
                        .getContainerNodeParser()
                        .parse(Collections.singletonList(element.getDomElement()), schemaNode);
        } else {
            //server responded with no content
            return null;
        }
    }

    @Override
    public DOMNotification parseNotification(final String notificationString) {
        try {
            final XmlElement xmlElement = XmlElement.fromString(notificationString);
            final List<XmlElement> childElements = xmlElement.getChildElements();
            Preconditions.checkArgument(childElements.size() == 2);
            final XmlElement eventTime = childElements.get(0);
            final XmlElement body = childElements.get(1);
            final NotificationDefinition schema = getNotificationSchemaNode(URI.create(body.getNamespace()), body.getName());
            final ContainerSchemaNode schemaNode = NetconfMessageTransformUtil.createSchemaForNotification(schema);
            final ContainerNode parse = DomToNormalizedNodeParserFactory
                    .getInstance(DomUtils.defaultValueCodecProvider(), schemaContext)
                    .getContainerNodeParser()
                    .parse(Collections.singletonList(body.getDomElement()), schemaNode);

            return new RestconfDeviceNotification(parse, eventTime.getTextContent());
        } catch (final DocumentedException e) {
            throw new IllegalStateException(MALFORMED_REPLY, e);
        }

    }

    private NotificationDefinition getNotificationSchemaNode(final URI namespace, final String notificationContentName) {
        final Set<Module> moduleByNamespace = schemaContext.findModuleByNamespace(namespace);
        final Module module = Collections.max(moduleByNamespace, new Comparator<Module>() {
            @Override
            public int compare(final Module o1, final Module o2) {
                return o1.getQNameModule().getRevision().compareTo(o2.getRevision());
            }
        });
        for (final NotificationDefinition notificationDefinition : module.getNotifications()) {
            if (notificationContentName.equals(notificationDefinition.getQName().getLocalName())) {
                final Collection<DataSchemaNode> childNodes = notificationDefinition.getChildNodes();
                Preconditions.checkState(childNodes.size() == 1);
                return notificationDefinition;
            }
        }
        throw new IllegalStateException("Schema node for notification not found " + notificationContentName);
    }

}
