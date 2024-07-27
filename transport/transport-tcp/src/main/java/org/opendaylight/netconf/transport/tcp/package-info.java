/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * TCP transport layer. This does not constitute a {@code NETCONF Secure Transport}, but rather captures common aspects
 * of TCP-based secure transports, such as SSH and TLS. Configuration follows
 * <a href="https://datatracker.ietf.org/doc/html/draft-ietf-netconf-tcp-client-server-13">
 * draft-ietf-netconf-tcp-client-server</a>.
 *
 * <p>
 * While this is strictly not a secure transport, it provides a TransportStack and thus can be used as is, for example
 * for testing purposes.
 *
 * <p>
 * The three primary entry points into this package are {@link NettyTransportSupport}, {@link TCPClient} and
 * {@link TCPServer}.
 */
@org.osgi.annotation.bundle.Export
package org.opendaylight.netconf.transport.tcp;
