/*
 * Copyright (c) 2017 Inocybe Technologies, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import org.junit.Before;
import org.mockito.Mock;
import org.opendaylight.controller.md.sal.binding.test.AbstractConcurrentDataBrokerTest;
import org.opendaylight.controller.sal.core.api.model.SchemaService;

public class RestconfProviderImplTest extends AbstractConcurrentDataBrokerTest {
    private RestconfProviderImpl plop;

    @Mock
    private SchemaService schemaService;

    @Before
    public setup() {
        plop = new RestconfProviderImpl(this.getDomBroker()., this.)
    }
}
