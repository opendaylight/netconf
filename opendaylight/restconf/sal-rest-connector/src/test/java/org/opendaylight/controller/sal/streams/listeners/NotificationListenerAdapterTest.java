/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.streams.listeners;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.BinaryTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.BitsTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.BooleanTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.IntegerTypeDefinition;
import org.opendaylight.yangtools.yang.model.util.BaseTypes;

public class NotificationListenerAdapterTest {

    @Mock
    private DOMNotification notification;
    @Mock
    private SchemaContext globalSchema;
    @Mock
    private ContainerNode node;
    @Mock
    private Module module;

    private final QName qname = QName.create("stream-namespace", "2016-08-18", "stream-localname");

    @Before
    public void init(){
        MockitoAnnotations.initMocks(this);
        Mockito.when(this.globalSchema.getQName()).thenReturn(this.qname);
        Mockito.when(
                this.globalSchema.findModuleByNamespaceAndRevision(this.qname.getNamespace(), this.qname.getRevision()))
                .thenReturn(this.module);
        Mockito.when(this.module.getName()).thenReturn("stream");
        ControllerContext.getInstance().setGlobalSchema(this.globalSchema);
    }

    @Test
    public void onNotifiLeafIntegerTest() {
        makeTestWithSpecValues("leaf-module", "leaf", IntegerTypeDefinition.class, 5, BaseTypes.INT16_QNAME);
    }

    @Test(expected = NumberFormatException.class)
    public void onNotifiLeafeIntegerNegTest() {
        makeTestWithSpecValues("leaf-module", "leaf", IntegerTypeDefinition.class, "a", BaseTypes.INT16_QNAME);
    }

    @Test
    public void onNotifiLeafeBinaryTest() {
        makeTestWithSpecValues("leaf-module", "leaf", BinaryTypeDefinition.class, "0xD", BaseTypes.BINARY_QNAME);
    }

    @Test
    public void onNotifiLeafeBitsTest() {
        makeTestWithSpecValues("leaf-module", "leaf", BitsTypeDefinition.class, "one", BaseTypes.BITS_QNAME);
    }

    @Test(expected = IllegalArgumentException.class)
    public void onNotifiLeafeBitsNegTest() {
        makeTestWithSpecValues("leaf-module", "leaf", BitsTypeDefinition.class, "two", BaseTypes.BITS_QNAME);
    }

    @Test
    public void onNotifiLeafeBooleanTrueTest() {
        makeTestWithSpecValues("leaf-module", "leaf", BooleanTypeDefinition.class, true, BaseTypes.BOOLEAN_QNAME);
    }

    @Test
    public void onNotifiLeafeBooleanFalseTest() {
        makeTestWithSpecValues("leaf-module", "leaf", BooleanTypeDefinition.class, false, BaseTypes.BOOLEAN_QNAME);
    }

    @Test(expected = IllegalArgumentException.class)
    public void onNotifiLeafeBooleanNegTest() {
        makeTestWithSpecValues("leaf-module", "leaf", BooleanTypeDefinition.class, "", BaseTypes.BOOLEAN_QNAME);
    }

    private void makeTestWithSpecValues(final String namespace, final String localName,
            final Class<? extends TypeDefinition> type, final Object value, final QName baseTypeQName) {
        final List<SchemaPath> qnameList = prepareListOfQname(namespace, localName);
        final NotificationListenerAdapter notificationListenerAdapter = Notificator
                .createNotificationListener(qnameList, "leafInteger", "JSON").get(0);

        final DOMNotification leafNotifi = Mockito.mock(DOMNotification.class);
        Mockito.when(leafNotifi.getType()).thenReturn(qnameList.get(0));

        final ContainerNode contNode = Mockito.mock(ContainerNode.class);
        Mockito.when(leafNotifi.getBody()).thenReturn(contNode);

        final LeafNode leafNode = Mockito.mock(LeafNode.class);
        Mockito.when(leafNode.getNodeType()).thenReturn(qnameList.get(0).getLastComponent());
        Mockito.when(leafNode.getValue()).thenReturn(value);

        final List<DataContainerChild<? extends PathArgument, ?>> childs = new ArrayList<>();
        childs.add(leafNode);
        Mockito.when(contNode.getValue()).thenReturn(childs);

        prepareSchemaCtxSchemaNodeDefNotifi(qnameList.get(0).getLastComponent(), type, baseTypeQName);
        notificationListenerAdapter.onNotification(leafNotifi);
    }

    private void prepareSchemaCtxSchemaNodeDefNotifi(final QName lastComponent,
            final Class<? extends TypeDefinition> type,
            final QName baseTypeQName) {
        final SchemaContext schema = Mockito.mock(SchemaContext.class);
        Mockito.when(schema.getQName()).thenReturn(lastComponent);

        final Module module = Mockito.mock(Module.class);
        Mockito.when(schema.findModuleByNamespaceAndRevision(lastComponent.getNamespace(), lastComponent.getRevision()))
                .thenReturn(module);
        Mockito.when(module.getName()).thenReturn(lastComponent.getLocalName() + "-module");

        final NotificationDefinition notifiDef = Mockito.mock(NotificationDefinition.class);
        Mockito.when(notifiDef.getQName()).thenReturn(lastComponent);

        final LeafSchemaNode leafSchemaNode = Mockito.mock(LeafSchemaNode.class);
        Mockito.when(leafSchemaNode.getQName()).thenReturn(lastComponent);

        final TypeDefinition typeDef = Mockito.mock(type);
        if (type.equals(BitsTypeDefinition.class)) {
            final BitsTypeDefinition.Bit mockBit1 = Mockito.mock(BitsTypeDefinition.Bit.class);
            Mockito.when(mockBit1.getName()).thenReturn("one");
            final List<BitsTypeDefinition.Bit> bitList = Lists.newArrayList(mockBit1);
            Mockito.when(((BitsTypeDefinition) typeDef).getBits()).thenReturn(bitList);
        }
        Mockito.when(leafSchemaNode.getType()).thenReturn(typeDef);
        Mockito.when(typeDef.getQName()).thenReturn(baseTypeQName);

        final Collection<DataSchemaNode> childs = new ArrayList<>();
        childs.add(leafSchemaNode);
        Mockito.when(notifiDef.getChildNodes()).thenReturn(childs);


        final Set<NotificationDefinition> notifis = new HashSet<>();
        notifis.add(notifiDef);
        Mockito.when(module.getNotifications()).thenReturn(notifis);

        ControllerContext.getInstance().setGlobalSchema(schema);
    }

    private List<SchemaPath> prepareListOfQname(final String namespace, final String localName) {
        final List<SchemaPath> path = new ArrayList<>();
        path.add(SchemaPath.create(true, QName.create(namespace, "2016-09-26", localName)));
        return path;
    }

    @Test
    public void onNotifiTest() {
        final List<SchemaPath> paths = new ArrayList<>();
        final SchemaPath schema = SchemaPath.create(true, this.qname);
        paths.add(schema);
        final List<NotificationListenerAdapter> listeners =
                Notificator.createNotificationListener(paths, "streamName", "JSON");
        final NotificationListenerAdapter listener = listeners.get(0);
        Mockito.when(this.notification.getType()).thenReturn(schema);
        Mockito.when(this.notification.getBody()).thenReturn(this.node);
        final List<DataContainerChild<? extends PathArgument, ?>> childs = new ArrayList<>();
        final AugmentationNode augmentLeaf = Mockito.mock(AugmentationNode.class);
        childs.add(augmentLeaf);
        final Collection<DataContainerChild<? extends PathArgument, ?>> augmentChilds = new ArrayList<>();
        final LeafNode leaf = Mockito.mock(LeafNode.class);
        final QName leafAugmentNodeQName = QName.create("augment-module", "2016-08-18", "leafik");
        Mockito.when(leaf.getNodeType()).thenReturn(leafAugmentNodeQName);
        Mockito.when(leaf.getValue()).thenReturn("augmentLeafik");
        augmentChilds.add(leaf);
        final Module augmentModule = Mockito.mock(Module.class);
        Mockito.when(this.globalSchema.findModuleByNamespaceAndRevision(leafAugmentNodeQName.getNamespace(),
                leafAugmentNodeQName.getRevision())).thenReturn(augmentModule);
        Mockito.when(augmentModule.getName()).thenReturn("augment-module");
        Mockito.when(augmentLeaf.getValue()).thenReturn(augmentChilds);
        Mockito.when(this.node.getValue()).thenReturn(childs);
        listener.onNotification(this.notification);
    }
}
