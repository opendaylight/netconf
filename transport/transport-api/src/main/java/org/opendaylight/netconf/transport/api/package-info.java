/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * NETCONF Secure Transport layer interfaces. These cover establishment of a the persistent connection and managing its
 * lifecycle. See <a href="https://www.rfc-editor.org/rfc/rfc6241#page-9">RFC6241 Figure 1</a> for the architectural
 * placement in the NETCONF stack.
 *
 * <p>
 * In traditional NETCONF operation NETCONF server listens for TCP connections and NETCONF clients connect to it. In
 * <a href="https://www.rfc-editor.org/rfc/rfc8071#section-2">Call Home</a>, the TCP layer operates the other way
 * around, i.e. NETCONF clients listen for TCP connections and NETCONF servers connect to it. Once the TCP session is
 * established, though, it is the client which initiates both transport-level and NETCONF-level handshakes.
 */
package org.opendaylight.netconf.transport.api;