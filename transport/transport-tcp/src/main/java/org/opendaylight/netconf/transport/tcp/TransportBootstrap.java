/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev220524.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev220524.TcpServerGrouping;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * Abstract base class for TCP-based {@link TransportStack} bootstraps.
 */
@Beta
public abstract class TransportBootstrap implements Immutable {
    private final @NonNull TransportChannelListener listener;

    protected TransportBootstrap(final TransportChannelListener listener) {
        this.listener = requireNonNull(listener);
    }

    public abstract @NonNull CompletionStage<TransportStack> initiate(TcpClientGrouping connectParams)
        throws UnsupportedConfigurationException;

    public abstract @NonNull CompletionStage<TransportStack> listen(TcpServerGrouping listenParams)
        throws UnsupportedConfigurationException;

    protected final @NonNull TransportChannelListener listener() {
        return listener;
    }

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
    }

    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("listener", listener);
    }

    protected static final int portNumber(final @Nullable PortNumber port, final int defaultPort) {
        if (port != null) {
            final int portVal = port.getValue().toJava();
            if (portVal != 0) {
                return portVal;
            }
        }
        return defaultPort;
    }

    protected static final <O, T> @NonNull T require(final O obj, final Function<O, T> method, final String attribute)
            throws UnsupportedConfigurationException {
        final var ret = method.apply(obj);
        if (ret == null) {
            throw new UnsupportedConfigurationException("Missing mandatory attribute " + attribute);
        }
        return ret;
    }
}
