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
import org.opendaylight.netconf.transport.http.HTTPServerSession;
import org.opendaylight.yangtools.concepts.Immutable;

/**
 * {@link WebHostResource} injection mechanism. Instances of this interface are expected to be injected into
 * {@link HTTPServerSession} implementations, which are expected to invoke {@link #createInstance()} with a unique path
 * and wire invocations of {@code HTTPServerSession.prepareRequest()} to
 * {@link WebHostResource#prepare(org.opendaylight.netconf.transport.http.ImplementedMethod, java.net.URI,
 * io.netty.handler.codec.http.HttpHeaders, org.opendaylight.netconf.transport.http.SegmentPeeler, XRD)}.
 *
 * <p>Implementations are required to be {@link Immutable effectively immutable}.
 */
@Beta
@NonNullByDefault
public interface WebHostResourceProvider extends Immutable {

    // TODO: return List<String>
    String defaultPath();

    // TODO: List<String> path
    WebHostResourceInstance createInstance(String path);
}
