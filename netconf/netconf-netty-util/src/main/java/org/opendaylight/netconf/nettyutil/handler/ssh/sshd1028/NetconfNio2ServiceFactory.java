/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.sshd1028;

import java.lang.reflect.Field;
import java.nio.channels.AsynchronousChannelGroup;
import java.security.AccessController;
import java.security.PrivilegedAction;
import org.opendaylight.netconf.shaded.sshd.common.FactoryManager;
import org.opendaylight.netconf.shaded.sshd.common.io.IoConnector;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.common.io.nio2.Nio2ServiceFactory;
import org.opendaylight.netconf.shaded.sshd.common.util.threads.CloseableExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom Nio2ServiceFactory which creates instances of NetconfNio2Connector instead of Nio2Connector.
 * Should be removed when SSHD-1028 is fixed.
 */
public class NetconfNio2ServiceFactory extends Nio2ServiceFactory {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfNio2ServiceFactory.class);
    private static final Field FIELD_GROUP;

    static {
        final Field fieldGroup;
        try {
            fieldGroup = NetconfNio2ServiceFactory.class.getSuperclass().getDeclaredField("group");
        } catch (NoSuchFieldException e) {
            LOG.error("Cannot access the ChannelGroup from the " + "Nio2ServiceFactory");
            throw new ExceptionInInitializerError(e);
        }

        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            fieldGroup.setAccessible(true);
            return null;
        });

        FIELD_GROUP = fieldGroup;
    }

    public NetconfNio2ServiceFactory(final FactoryManager factoryManager, final CloseableExecutorService service) {
        super(factoryManager, service);
    }

    @Override
    public IoConnector createConnector(final IoHandler handler) {
        if (FIELD_GROUP != null) {
            try {
                final AsynchronousChannelGroup group = (AsynchronousChannelGroup)FIELD_GROUP.get(this);
                return autowireCreatedService(new NetconfNio2Connector(getFactoryManager(), handler, group));
            } catch (IllegalAccessException e) {
                LOG.error("NetconfNio2Connector cannot be instanciated. Creating default Nio2Connector instead.");
            }
        }

        return super.createConnector(handler);
    }
}
