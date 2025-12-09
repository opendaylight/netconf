/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.api;

/**
 * SSH connection algorithms listener.
 */
public interface SSHNegotiatedAlgListener {
    /**
     * Invoked when SSH connection algorithms are negotiated.
     *
     * @param kexAlgorithm key exchange algorithm
     * @param hostKey server host key algorithm
     * @param encryption encryption algorithm
     * @param mac mac algorithm
     */
    void onAlgorithmsNegotiated(String kexAlgorithm, String hostKey, String encryption, String mac);
}
