/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.controller.config.yang.md.sal.rest.connector.RestConnectorRuntimeRegistration;
import org.opendaylight.controller.config.yang.md.sal.rest.connector.RestConnectorRuntimeRegistrator;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.controller.sal.core.api.Provider;
import org.opendaylight.netconf.sal.restconf.impl.RestconfProviderImpl;
import org.opendaylight.restconf.RestConnectorProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;

public class RestconfWrapperProvidersTest {

    private static final PortNumber PORT = new PortNumber(8181);

    @Test
    public void initRWPTest() {
        final RestconfWrapperProviders rwp = new RestconfWrapperProviders(PORT);
        assertNotNull(rwp);
    }

    @Test
    public void registerTest() {
        final RestconfWrapperProviders rwp = new RestconfWrapperProviders(PORT);
        assertNotNull(rwp);

        final Broker broker = mock(Broker.class);
        rwp.registerProviders(broker);

        Mockito.verify(broker, times(2)).registerProvider(Mockito.any(Provider.class));
    }

    @Test
    public void runtimeRegistrationTest() {
        final RestconfWrapperProviders rwp = new RestconfWrapperProviders(PORT);
        assertNotNull(rwp);

        final RestConnectorRuntimeRegistrator runtimeRegistrator = mock(RestConnectorRuntimeRegistrator.class);
        final RestConnectorRuntimeRegistration value = mock(RestConnectorRuntimeRegistration.class);
        when(runtimeRegistrator.register(any())).thenReturn(value);
        final RestConnectorRuntimeRegistration runtimeRegistration = rwp.runtimeRegistration(runtimeRegistrator);

        assertEquals(value, runtimeRegistration);
    }

    @Test
    public void closeTest() throws Exception {
        final RestconfWrapperProviders rwp = new RestconfWrapperProviders(PORT);
        assertNotNull(rwp);

        final RestconfProviderImpl draft2 = mock(RestconfProviderImpl.class);
        final RestConnectorProvider draft18 = mock(RestConnectorProvider.class);

        setDeclaredField(rwp, "providerDraft02", draft2);
        setDeclaredField(rwp, "providerDraft18", draft18);

        rwp.close();

        verify(draft2, times(1)).close();
        verify(draft18, times(1)).close();
    }

    private static void setDeclaredField(final Object rwp, final String name, final Object value) throws Exception {
        final Field declaredField = rwp.getClass().getDeclaredField(name);
        declaredField.setAccessible(true);
        declaredField.set(rwp, value);
    }
}
