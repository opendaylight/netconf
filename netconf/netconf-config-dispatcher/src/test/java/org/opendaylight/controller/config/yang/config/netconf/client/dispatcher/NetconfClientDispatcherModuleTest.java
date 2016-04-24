/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.config.yang.config.netconf.client.dispatcher;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.ObjectName;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opendaylight.controller.config.api.ConflictingVersionException;
import org.opendaylight.controller.config.api.ValidationException;
import org.opendaylight.controller.config.api.jmx.CommitStatus;
import org.opendaylight.controller.config.manager.impl.AbstractConfigTest;
import org.opendaylight.controller.config.manager.impl.factoriesresolver.HardcodedModuleFactoriesResolver;
import org.opendaylight.controller.config.util.ConfigTransactionJMXClient;
import org.opendaylight.controller.config.yang.netty.threadgroup.NettyThreadgroupModuleFactory;
import org.opendaylight.controller.config.yang.netty.threadgroup.NettyThreadgroupModuleMXBean;
import org.opendaylight.controller.config.yang.netty.timer.HashedWheelTimerModuleFactory;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import io.netty.channel.EventLoopGroup;
import io.netty.util.Timer;

public class NetconfClientDispatcherModuleTest extends AbstractConfigTest{

    private NetconfClientDispatcherModuleFactory factory;
    private final String instanceName = "dispatch";

    @Before
    public void setUp() throws Exception {
        factory = new NetconfClientDispatcherModuleFactory();
        super.initConfigTransactionManagerImpl(new HardcodedModuleFactoriesResolver(mockedContext,factory,
                new NettyThreadgroupModuleFactory(),
                new HashedWheelTimerModuleFactory()));

        doAnswer(new Answer<Filter>() {
            @Override
            public Filter answer(InvocationOnMock invocation) {
                String str = invocation.getArgumentAt(0, String.class);
                Filter mockFilter = mock(Filter.class);
                doReturn(str).when(mockFilter).toString();
                return mockFilter;
            }
        }).when(mockedContext).createFilter(anyString());
        doNothing().when(mockedContext).addServiceListener(any(ServiceListener.class), anyString());

        setupMockService(EventLoopGroup.class);
        setupMockService(Timer.class);
    }

    private void setupMockService(Class<?> serviceInterface) throws Exception {
        ServiceReference<?> mockServiceRef = mock(ServiceReference.class);
        doReturn(new ServiceReference[]{mockServiceRef}).when(mockedContext).
                getServiceReferences(anyString(), contains(serviceInterface.getName()));
        doReturn(mock(serviceInterface)).when(mockedContext).getService(mockServiceRef);
    }

    @Test
    public void testCreateBean() throws InstanceAlreadyExistsException, ValidationException, ConflictingVersionException {
        ConfigTransactionJMXClient transaction = configRegistryClient.createTransaction();

        createInstance(transaction, instanceName, "timer", "thGroup");
        createInstance(transaction, instanceName + 2, "timer2", "thGroup2");
        transaction.validateConfig();
        CommitStatus status = transaction.commit();

        assertBeanCount(2, factory.getImplementationName());
        assertStatus(status, 2 + 4, 0, 0);
    }

    @Test
    public void testReusingOldInstance() throws InstanceAlreadyExistsException, ConflictingVersionException, ValidationException {

        ConfigTransactionJMXClient transaction = configRegistryClient.createTransaction();
        createInstance(transaction, instanceName, "timer", "thGroup");

        transaction.commit();

        transaction = configRegistryClient.createTransaction();
        assertBeanCount(1, factory.getImplementationName());
        CommitStatus status = transaction.commit();

        assertBeanCount(1, factory.getImplementationName());
        assertStatus(status, 0, 0, 3);
    }

    @Test
    public void testReconfigure() throws InstanceAlreadyExistsException, ConflictingVersionException,
            ValidationException, InstanceNotFoundException {

        ConfigTransactionJMXClient transaction = configRegistryClient.createTransaction();
        createInstance(transaction, instanceName, "timer", "thGroup");

        transaction.commit();

        transaction = configRegistryClient.createTransaction();
        assertBeanCount(1, factory.getImplementationName());
        NetconfClientDispatcherModuleMXBean mxBean = transaction.newMBeanProxy(
                transaction.lookupConfigBean(NetconfClientDispatcherModuleFactory.NAME, instanceName),
                NetconfClientDispatcherModuleMXBean.class);
        mxBean.setBossThreadGroup(getThreadGroup(transaction, "group2"));
        CommitStatus status = transaction.commit();

        assertBeanCount(1, factory.getImplementationName());
        assertStatus(status, 1, 1, 2);
    }

    private ObjectName createInstance(ConfigTransactionJMXClient transaction, String instanceName, String timerName, String threadGroupName)
            throws InstanceAlreadyExistsException {
        ObjectName nameCreated = transaction.createModule(factory.getImplementationName(), instanceName);
        NetconfClientDispatcherModuleMXBean mxBean = transaction.newMBeanProxy(nameCreated, NetconfClientDispatcherModuleMXBean.class);
        ObjectName thGroup = getThreadGroup(transaction, threadGroupName);
        mxBean.setBossThreadGroup(thGroup);
        mxBean.setWorkerThreadGroup(thGroup);
        mxBean.setTimer(getTimer(transaction, timerName));
        return nameCreated;
    }

    private ObjectName getTimer(ConfigTransactionJMXClient transaction, String name) throws InstanceAlreadyExistsException {
        return transaction.createModule(HashedWheelTimerModuleFactory.NAME, name);
    }

    private ObjectName getThreadGroup(ConfigTransactionJMXClient transaction, String name) throws InstanceAlreadyExistsException {
        ObjectName nameCreated = transaction.createModule(NettyThreadgroupModuleFactory.NAME, name);
        NettyThreadgroupModuleMXBean mxBean = transaction.newMXBeanProxy(nameCreated, NettyThreadgroupModuleMXBean.class);
        mxBean.setThreadCount(1);
        return nameCreated;
    }
}
