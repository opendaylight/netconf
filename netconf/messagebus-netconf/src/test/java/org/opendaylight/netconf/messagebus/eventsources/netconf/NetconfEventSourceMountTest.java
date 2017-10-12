/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.messagebus.eventsources.netconf;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.CreateSubscriptionInput;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.Netconf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.StreamBuilder;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class NetconfEventSourceMountTest {

    public static final String STREAM_1 = "stream-1";
    public static final String STREAM_2 = "stream-2";
    @Mock
    private DOMMountPoint domMountPoint;
    @Mock
    DOMDataBroker dataBroker;
    @Mock
    DOMRpcService rpcService;
    @Mock
    private DOMDataReadOnlyTransaction tx;
    private NetconfEventSourceMount mount;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(Optional.of(dataBroker)).when(domMountPoint).getService(DOMDataBroker.class);
        doReturn(Optional.of(rpcService)).when(domMountPoint).getService(DOMRpcService.class);
        doReturn(Optional.of(mock(DOMNotificationService.class))).when(domMountPoint)
                .getService(DOMNotificationService.class);
        doReturn(tx).when(dataBroker).newReadOnlyTransaction();
        final YangInstanceIdentifier path = YangInstanceIdentifier.builder().node(Netconf.QNAME).node(Streams.QNAME)
                .build();
        final NormalizedNode<?, ?> streamsNode = NetconfTestUtils.getStreamsNode(STREAM_1, STREAM_2);
        doReturn(Futures.immediateCheckedFuture(Optional.of(streamsNode))).when(tx).read(LogicalDatastoreType
                .OPERATIONAL, path);
        mount = new NetconfEventSourceMount(NetconfTestUtils.getNode("node-1"), domMountPoint);
    }

    @Test
    public void testInvokeCreateSubscription() throws Exception {
        Stream stream = new StreamBuilder()
                .setName(new StreamNameType(STREAM_1))
                .build();
        mount.invokeCreateSubscription(stream, Optional.absent());
        final SchemaPath type = SchemaPath.create(true, QName.create(CreateSubscriptionInput.QNAME,
                "create-subscription"));
        ArgumentCaptor<ContainerNode> captor = ArgumentCaptor.forClass(ContainerNode.class);
        verify(rpcService).invokeRpc(eq(type), captor.capture());
        Assert.assertEquals(STREAM_1, getStreamName(captor.getValue()));
    }

    @Test
    public void testInvokeCreateSubscription1() throws Exception {
        Stream stream = new StreamBuilder()
                .setName(new StreamNameType(STREAM_1))
                .setReplaySupport(true)
                .build();
        final Date date = new Date();
        mount.invokeCreateSubscription(stream, Optional.of(date));
        final SchemaPath type = SchemaPath.create(true, QName.create(CreateSubscriptionInput.QNAME,
                "create-subscription"));
        ArgumentCaptor<ContainerNode> captor = ArgumentCaptor.forClass(ContainerNode.class);
        verify(rpcService).invokeRpc(eq(type), captor.capture());
        Assert.assertEquals(STREAM_1, getStreamName(captor.getValue()));
        final String expDate = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(date.toInstant().atZone(ZoneId
                .systemDefault()));
        final Optional<LeafNode> actual = (Optional<LeafNode>) getDate(captor.getValue());
        Assert.assertTrue(actual.isPresent());
        String actualDate = (String) actual.get().getValue();
        Assert.assertEquals(expDate, actualDate);
    }

    @Test
    public void testInvokeCreateSubscription2() throws Exception {
        Stream stream = new StreamBuilder()
                .setName(new StreamNameType(STREAM_1))
                .setReplaySupport(true)
                .build();
        mount.invokeCreateSubscription(stream, Optional.absent());
        final SchemaPath type = SchemaPath.create(true, QName.create(CreateSubscriptionInput.QNAME,
                "create-subscription"));
        ArgumentCaptor<ContainerNode> captor = ArgumentCaptor.forClass(ContainerNode.class);
        verify(rpcService).invokeRpc(eq(type), captor.capture());
        Assert.assertEquals(STREAM_1, getStreamName(captor.getValue()));
        final Optional<LeafNode> date = (Optional<LeafNode>) getDate(captor.getValue());
        Assert.assertFalse(date.isPresent());

    }

    @Test
    public void testGetAvailableStreams() throws Exception {
        final List<Stream> availableStreams = mount.getAvailableStreams();
        Assert.assertEquals(2, availableStreams.size());
        final List<String> streamNames = Lists.transform(availableStreams, new Function<Stream, String>() {
            @Nullable
            @Override
            public String apply(@Nullable final Stream input) {
                return input.getName().getValue();
            }
        });
        streamNames.contains(STREAM_1);
        streamNames.contains(STREAM_2);
    }

    private static String getStreamName(final ContainerNode value) {
        YangInstanceIdentifier.NodeIdentifier stream =
                new YangInstanceIdentifier.NodeIdentifier(QName.create(CreateSubscriptionInput.QNAME, "stream"));
        return (String) value.getChild(stream).get().getValue();
    }

    private static Optional<?> getDate(final ContainerNode value) {
        YangInstanceIdentifier.NodeIdentifier startTime =
                new YangInstanceIdentifier.NodeIdentifier(QName.create(CreateSubscriptionInput.QNAME, "startTime"));
        return Optional.fromJavaUtil(value.getChild(startTime));
    }
}