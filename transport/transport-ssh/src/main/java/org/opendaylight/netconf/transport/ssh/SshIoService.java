/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import org.opendaylight.netconf.shaded.sshd.common.FactoryManager;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoService;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoServiceFactory;

final class SshIoService extends NettyIoService {
    SshIoService(final FactoryManager factoryManager, final IoHandler handler) {
        super((NettyIoServiceFactory) factoryManager.getIoServiceFactory(), handler);

        // Required to keep things working, but does not need to be functional at all.
        //
        // Under normal circumstances, this group is used to track all open channels, so that we can close them when
        // we are shutdown.
        //
        // We do not need to do that, as we track the underlying transport instead, hence any channels tracked here will
        // disappear when we shut down the underlay.
        channelGroup = CompatChannelGroup.INSTANCE;
    }
}
