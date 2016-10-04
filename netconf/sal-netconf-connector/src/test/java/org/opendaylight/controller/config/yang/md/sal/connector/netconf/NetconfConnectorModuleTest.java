/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.md.sal.connector.netconf;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.lang.reflect.Field;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.config.api.DependencyResolver;
import org.opendaylight.controller.config.api.ModuleIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.DomainName;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPassword;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;

public class NetconfConnectorModuleTest {

    private static final String MODULE_NAME = "module-name";
    private static final Host HOST = new Host(new DomainName("localhost"));
    private static final PortNumber PORT = new PortNumber(17830);
    private static final Boolean TCP_ONLY = Boolean.FALSE;
    @Mock
    private DependencyResolver resolver;
    @Mock
    private BindingAwareBroker bindingRegistry;
    @Mock
    private BindingAwareBroker.ConsumerContext session;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private WriteTransaction transaction;
    private NetconfConnectorModule module;
    private static final LoginPassword LOGIN_PASSWORD = new LoginPasswordBuilder()
            .setUsername("admin")
            .setPassword("admin")
            .build();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        module = new NetconfConnectorModule(new ModuleIdentifier("factory", MODULE_NAME), resolver);
        setViaReflection(module, "bindingRegistryDependency", bindingRegistry);
        setViaReflection(module, "address", HOST);
        setViaReflection(module, "port", PORT);
        setViaReflection(module, "tcpOnly", TCP_ONLY);
        setViaReflection(module, "username", LOGIN_PASSWORD.getUsername());
        setViaReflection(module, "password", LOGIN_PASSWORD.getPassword());
        doReturn(dataBroker).when(session).getSALService(DataBroker.class);
        doReturn(transaction).when(dataBroker).newWriteOnlyTransaction();
        doReturn(Futures.immediateCheckedFuture(null)).when(transaction).submit();
        module.customValidation();
    }

    @Test
    public void onSessionInitialized() throws Exception {
        final AutoCloseable instance = module.createInstance();
        verify(bindingRegistry).registerConsumer(module);
        final NodeId nodeId = new NodeId(MODULE_NAME);
        final KeyedInstanceIdentifier<Node, NodeKey> id = InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId("topology-netconf")))
                .child(Node.class, new NodeKey(nodeId));
        module.onSessionInitialized(session);
        final ArgumentCaptor<Node> createdNodeCaptor = ArgumentCaptor.forClass(Node.class);
        verify(transaction).put(eq(LogicalDatastoreType.CONFIGURATION), eq(id), createdNodeCaptor.capture());
        verifyNode(nodeId, createdNodeCaptor.getValue());

        instance.close();
        verify(transaction).delete(LogicalDatastoreType.CONFIGURATION, id);
        verify(transaction, times(2)).submit();
    }

    private void verifyNode(final NodeId nodeId, final Node node) {
        Assert.assertEquals(nodeId, node.getNodeId());
        final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
        Assert.assertEquals(HOST, netconfNode.getHost());
        Assert.assertEquals(PORT, netconfNode.getPort());
        Assert.assertEquals(LOGIN_PASSWORD, netconfNode.getCredentials());
        Assert.assertEquals(TCP_ONLY, netconfNode.isTcpOnly());
    }

    private void setViaReflection(final Object object, final String fieldName, final Object value) {
        try {
            final Field field = AbstractNetconfConnectorModule.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(object, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

}