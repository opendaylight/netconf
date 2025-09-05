/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.shaded.sshd.common.channel.ChannelAsyncOutputStream;
import org.opendaylight.netconf.shaded.sshd.common.io.IoOutputStream;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;

final class TransportUtils {
    private TransportUtils() {
        // utility class
    }

    static <K, V> @NonNull List<V> mapValues(final Map<K, ? extends V> map, final List<K> values,
            final String errorTemplate) throws UnsupportedConfigurationException {
        final var builder = ImmutableList.<V>builderWithExpectedSize(values.size());
        for (var value : values) {
            final var mapped = map.get(value);
            if (mapped == null) {
                throw new UnsupportedOperationException(String.format(errorTemplate, value));
            }
            builder.add(mapped);
        }
        return builder.build();
    }

    @FunctionalInterface
    interface ChannelInactive {

        void onChannelInactive() throws Exception;
    }

    static ChannelHandlerContext attachUnderlay(final IoOutputStream out, final TransportChannel underlay,
            final ChannelInactive inactive) {
        if (!(out instanceof ChannelAsyncOutputStream asyncOut)) {
            throw new VerifyException("Unexpected output " + out);
        }

        // Note that there may be multiple handlers already present on the channel, hence we are attaching last, but
        // from the logical perspective we are the head handlers.
        final var pipeline = underlay.channel().pipeline();

        // outbound packet handler, i.e. moving bytes from the channel into SSHD's pipeline
        pipeline.addLast(new OutboundChannelHandler(asyncOut));

        // invoke requested action on channel termination
        underlay.channel().closeFuture().addListener(future -> inactive.onChannelInactive());

        // last handler context is used as entry point to direct inbound packets (captured by SSH adapter)
        // back to same channel pipeline
        return pipeline.lastContext();
    }
}
