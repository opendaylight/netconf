/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http.rfc6415;

import com.google.common.annotations.Beta;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.HTTPServer;
import org.opendaylight.netconf.transport.http.HTTPServerSession;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * {@link WebHostResource} injection mechanism. Instances of this interface are expected to be injected into
 * {@link HTTPServerSession} implementations, which are expected to invoke {@link #createInstance(String)} with a unique
 * path and wire invocations of {@code HTTPServerSession.prepareRequest()} to
 * {@link WebHostResource#prepare(io.netty.channel.ChannelHandler, org.opendaylight.netconf.transport.http.ImplementedMethod, java.net.URI, io.netty.handler.codec.http.HttpHeaders, org.opendaylight.netconf.transport.http.SegmentPeeler, XRD)}.
 *
 * <p>This maps directly to <a href="https://en.wikipedia.org/wiki/Whiteboard_Pattern">the whiteboard pattern</a>, with
 * this interface being the registered {@code Service}. Each instance maps to an individual {code Event listener}.
 * Entity associated with a {@link HTTPServer} would typically act is the {@code Event source}, starting each lifecycle
 * with a call to {@link #createInstance(String)}.
 *
 * <p>Implementations are required to be {@link Immutable effectively immutable}.
 */
@Beta
@NonNullByDefault
public interface WebHostResourceProvider extends Immutable {
    /**
     * Return the default (preferred} path of this resource.
     *
     * @return the default (preferred} path of this resource
     */
    // TODO: return List<String>
    String defaultPath();

    /**
     * Create a new {@link WebHostResourceInstance} of this resource at specified path.
     *
     * @param path instance path
     * @return a new {@link WebHostResourceInstance}
     * @throws NullPointerException if {@code path} is {@code null}
     */
    // TODO: List<String> path
    WebHostResourceInstance createInstance(String path);
}
