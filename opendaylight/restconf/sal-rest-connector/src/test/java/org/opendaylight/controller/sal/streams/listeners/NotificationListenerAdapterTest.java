package org.opendaylight.controller.sal.streams.listeners;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

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
