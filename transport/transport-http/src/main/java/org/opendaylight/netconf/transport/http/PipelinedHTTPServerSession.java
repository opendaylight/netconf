/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

/**
 * An pipelined HTTP/1.1 server session.
 */
public abstract non-sealed class PipelinedHTTPServerSession extends HTTPServerSession {
    protected PipelinedHTTPServerSession(final HTTPScheme scheme) {
        super(scheme);
    }
}
