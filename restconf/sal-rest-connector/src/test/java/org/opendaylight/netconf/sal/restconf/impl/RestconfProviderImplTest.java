/*
 * Copyright (c) 2017 Inocybe Technologies, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import static org.mockito.MockitoAnnotations.initMocks;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.controller.md.sal.binding.test.AbstractConcurrentDataBrokerTest;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.netconf.sal.restconf.impl.jmx.Config;
import org.opendaylight.netconf.sal.restconf.impl.jmx.Operational;
import org.opendaylight.netconf.sal.restconf.impl.jmx.Rpcs;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;

public class RestconfProviderImplTest extends AbstractConcurrentDataBrokerTest {
    private RestconfProviderImpl restconfProvider;
    private static final PortNumber PORT = new PortNumber(17830);

    @Mock
    private SchemaService schemaService;
    @Mock
    private DOMRpcService rpcService;
    @Mock
    private DOMNotificationService reference;
    @Mock
    private DOMMountPointService mountPointService;

    @Before
    public void setUp() {
        initMocks(this);
        restconfProvider = new RestconfProviderImpl(this.getDomBroker(), schemaService,
                rpcService, reference, mountPointService, PORT);
        restconfProvider.start();
    }

    @After
    public void destroy() {
        restconfProvider.close();
    }

    @Test
    public void getConfigTest() {
        final Config result = restconfProvider.getConfig();
        Assert.assertEquals(result.getDelete().getFailedResponses().intValue(), 0);
        Assert.assertEquals(result.getGet().getSuccessfulResponses().intValue(), 0);
        Assert.assertEquals(result.getGet().getSuccessfulResponses().intValue(), 0);
    }

    @Test
    public void getOperationalTest() {
        final Operational result = restconfProvider.getOperational();
        Assert.assertEquals(result.getGet().getSuccessfulResponses().intValue(), 0);
        Assert.assertEquals(result.getGet().getReceivedRequests().intValue(), 0);
        Assert.assertEquals(result.getGet().getFailedResponses().intValue(), 0);
    }

    @Test
    public void getRpcsTest() {
        final Rpcs result = restconfProvider.getRpcs();
        Assert.assertEquals(result.getReceivedRequests().intValue(), 0);
    }
}
