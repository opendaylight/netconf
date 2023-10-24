/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.tcp;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import org.eclipse.jdt.annotation.NonNull;

/**
 * Utility encapsulation of a Netty one or two {@link EventLoopGroup}s. This class serves a building block for creating
 * {@link TCPTransportStack}s, in that in provides convenient {@link #newBootstrap()} and {@link #newServerBootstrap()}
 * methods, but is otherwise reuseable in other contexts as well.
 */
public class BootstrapFactory implements AutoCloseable {
    protected final @NonNull EventLoopGroup group;
    private final EventLoopGroup parentGroup;

    private BootstrapFactory(final EventLoopGroup group, final EventLoopGroup parentGroup) {
        this.group = requireNonNull(group);
        this.parentGroup = parentGroup;
    }

    public BootstrapFactory(final @NonNull String groupName, final int groupThreads) {
        this(NettyTransportSupport.newEventLoopGroup(groupName, groupThreads), null);
    }

    public BootstrapFactory(final @NonNull String groupName, final int groupThreads,
            final @NonNull String parentGroupName, final int parentGroupThreads) {
        this(NettyTransportSupport.newEventLoopGroup(groupName, groupThreads),
            NettyTransportSupport.newEventLoopGroup(parentGroupName, parentGroupThreads));
    }

    /**
     * Create a new {@link Bootstrap} based on this factory's {@link EventLoopGroup}s.
     *
     * @return A new {@link Bootstrap}
     */
    public final @NonNull Bootstrap newBootstrap() {
        return NettyTransportSupport.newBootstrap().group(group);
    }

    /**
     * Create a new {@link ServerBootstrap} based on this factory's {@link EventLoopGroup}s.
     *
     * @return A new {@link ServerBootstrap}
     */
    public final @NonNull ServerBootstrap newServerBootstrap() {
        return NettyTransportSupport.newServerBootstrap().group(parentGroup != null ? parentGroup : group, group);
    }

    @Override
    public final void close() {
        if (parentGroup != null) {
            parentGroup.shutdownGracefully();
        }
        group.shutdownGracefully();
    }

    @Override
    public final String toString() {
        return addToStringAttributes(MoreObjects.toStringHelper(this).omitNullValues()).toString();
    }

    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("group", group).add("parentGroup", parentGroup);
    }
}
