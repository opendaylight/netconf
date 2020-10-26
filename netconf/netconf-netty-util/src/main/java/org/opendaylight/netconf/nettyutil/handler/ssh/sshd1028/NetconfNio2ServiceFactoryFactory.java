/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.sshd1028;

import org.opendaylight.netconf.shaded.sshd.common.FactoryManager;
import org.opendaylight.netconf.shaded.sshd.common.io.IoServiceFactory;
import org.opendaylight.netconf.shaded.sshd.common.io.nio2.Nio2ServiceFactoryFactory;

/**
 * Custom Nio2ServiceFactoryFactory which creates instances of NetconfNio2ServiceFactory instead of Nio2ServiceFactory.
 * Should be removed when SSHD-1028 is fixed.
 */
public class NetconfNio2ServiceFactoryFactory extends Nio2ServiceFactoryFactory {

    public NetconfNio2ServiceFactoryFactory() {
        super(null);
    }

    @Override
    public IoServiceFactory create(final FactoryManager manager) {
        return new NetconfNio2ServiceFactory(manager, newExecutor());
    }
}
