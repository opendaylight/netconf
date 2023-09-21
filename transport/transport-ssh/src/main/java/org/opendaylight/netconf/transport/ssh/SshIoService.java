/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import io.netty.channel.group.ChannelGroup;
import org.opendaylight.netconf.shaded.sshd.common.FactoryManager;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoService;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoServiceFactory;

final class SshIoService extends NettyIoService {
    SshIoService(final FactoryManager factoryManager, final ChannelGroup group, final IoHandler handler) {
        super((NettyIoServiceFactory) factoryManager.getIoServiceFactory(), handler);
        channelGroup = requireNonNull(group);
    }
}
