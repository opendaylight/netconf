/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.rest;

import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.controller.sal.core.api.Broker;

/**
 * Unit tests for <code>RestConnectorProvider</code>
 * <p>
 * Tests are ignored because it is now not possible to mock <code>BundleContext</code> which is retrieved from static
 * {@link org.osgi.framework.FrameworkUtil#getBundle(Class)} method in private method
 * {@link RestConnectorProvider#getObjectFromBundleContext(Class, String)}.
 * <p>
 * After migrating to Jersey 2.x it will be possible to mock <code>BundleContext</code> and implement these tests.
 */
public class RestConnectorProviderTest {

    /**
     * Test of successful initialization of {@link RestConnectorProvider}.
     */
    @Ignore
    @Test
    public void restConnectorProviderInitTest() {
        // FIXME has to be implement after migrating to Jersey 2.x
    }

    /**
     * Test method {@link RestConnectorProvider#onSessionInitiated(Broker.ProviderSession)} with null input.
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void nullSessionRegistrationTest() {
        // FIXME has to be implement after migrating to Jersey 2.x
    }

    /**
     * Test for successful registration with {@link RestConnectorProvider#onSessionInitiated(Broker.ProviderSession)}
     * when all conditions are satisfied.
     * <p>
     * Condition 1: <code>Broker.ProviderSession</code> contains <code>SchemaService</code>
     * Condition 2: <code>BundleContext</code> contains <code>RestconfApplication</code>
     */
    @Ignore
    @Test
    public void successfulRegistrationTest() {
        // FIXME has to be implement after migrating to Jersey 2.x
    }

    /**
     * Negative test of registration with {@link RestConnectorProvider#onSessionInitiated(Broker.ProviderSession)} when
     * <code>Broker.ProviderSession</code> does not contain <code>SchemaService</code>.
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void withoutSchemaServiceRegistrationTest() {
        // FIXME has to be implement after migrating to Jersey 2.x
    }

    /**
     * Negative test of registration with {@link RestConnectorProvider#onSessionInitiated(Broker.ProviderSession)} when
     * <code>BundleContext</code> does not contain <code>RestconfApplication</code>.
     */
    @Ignore
    @Test(expected = NullPointerException.class)
    public void withoutRestconfApplicationRegistrationTest() {
        // FIXME has to be implement after migrating to Jersey 2.x
    }

    /**
     * Test of closing registration.
     */
    @Ignore
    @Test
    public void closeRegistrationTest() {
        // FIXME has to be implement after migrating to Jersey 2.x
    }
}
