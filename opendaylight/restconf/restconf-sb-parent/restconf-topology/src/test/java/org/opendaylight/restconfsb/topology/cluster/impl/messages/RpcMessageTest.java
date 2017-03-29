/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl.messages;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.Assert;
import org.junit.Test;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class RpcMessageTest {

    @Test
    public void testSerialization() throws Exception {
        final QName containerQName = QName.create("ns", "2016-05-11", "container");
        final SchemaPath schemaPath = SchemaPath.create(true, containerQName);
        final LeafNode<Object> leaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(containerQName, "leaf")))
                .withValue("value")
                .build();
        final ContainerNode container = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(containerQName))
                .withChild(leaf)
                .build();
        final RpcMessage msg = new RpcMessage(schemaPath, container);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        msg.writeExternal(new ObjectOutputStream(baos));
        final ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        final RpcMessage fromSerialized = new RpcMessage();
        fromSerialized.readExternal(ois);
        Assert.assertEquals(msg, fromSerialized);
    }
}