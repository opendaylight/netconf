/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.ChannelMatcher;
import java.util.Collection;
import java.util.Iterator;

/**
 * A fake implementation of a {@link ChannelGroup}.
 */
final class CompatChannelGroup implements ChannelGroup {
    static final CompatChannelGroup INSTANCE = new CompatChannelGroup();

    private CompatChannelGroup() {
        // No-op
    }

    @Override
    public boolean add(final Channel e) {
        return true;
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(final Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Channel> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] toArray(final T[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(final Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(final Collection<? extends Channel> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(final ChannelGroup o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String name() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Channel find(final ChannelId id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelGroupFuture write(final Object message) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelGroupFuture write(final Object message, final ChannelMatcher matcher) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelGroupFuture write(final Object message, final ChannelMatcher matcher, final boolean voidPromise) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelGroup flush() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelGroup flush(final ChannelMatcher matcher) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelGroupFuture writeAndFlush(final Object message) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public ChannelGroupFuture flushAndWrite(final Object message) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelGroupFuture writeAndFlush(final Object message, final ChannelMatcher matcher) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelGroupFuture writeAndFlush(final Object message, final ChannelMatcher matcher,
            final boolean voidPromise) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public ChannelGroupFuture flushAndWrite(final Object message, final ChannelMatcher matcher) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelGroupFuture disconnect() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelGroupFuture disconnect(final ChannelMatcher matcher) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelGroupFuture close() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelGroupFuture close(final ChannelMatcher matcher) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public ChannelGroupFuture deregister() {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public ChannelGroupFuture deregister(final ChannelMatcher matcher) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelGroupFuture newCloseFuture() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ChannelGroupFuture newCloseFuture(final ChannelMatcher matcher) {
        throw new UnsupportedOperationException();
    }
}
