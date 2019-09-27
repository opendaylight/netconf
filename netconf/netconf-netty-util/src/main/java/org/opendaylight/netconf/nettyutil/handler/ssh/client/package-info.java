/*
 * Copyright (c) 2019 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
/**
 * Utilities for integration between Apache SSHD and Netty. Contains the wiring logic to extend SshClient to allow
 * efficient shuffling of data towards the Netty channel.
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.client;