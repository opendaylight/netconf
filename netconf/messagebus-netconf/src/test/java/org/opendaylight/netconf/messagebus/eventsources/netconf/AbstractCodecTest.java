/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.messagebus.eventsources.netconf;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.opendaylight.mdsal.binding.dom.codec.impl.BindingCodecContext;
import org.opendaylight.mdsal.binding.runtime.spi.BindingRuntimeHelpers;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.Netconf;

public abstract class AbstractCodecTest {
    static BindingCodecContext SERIALIZER;

    @BeforeClass
    public static void beforeClass()  {
        SERIALIZER = new BindingCodecContext(BindingRuntimeHelpers.createRuntimeContext(Netconf.class));
    }

    @AfterClass
    public static void afterClass()  {
        SERIALIZER = null;
    }
}
