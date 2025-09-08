/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.common.kex.KeyExchangeFactory;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.transport.params.grouping.KeyExchange;

/**
 * An entity that determine how a particuar key exchange sequence should go. Instances of this class express a policy
 * on how a particular key exchange conversation showld go.
 */
@NonNullByDefault
interface KeyExchangePolicy {
    /**
     * {@return this policy's default, most preferrable way of conducting business}
     * @throws UnsupportedConfigurationException when an key echange cannot be initiated
     */
    List<KeyExchangeFactory> defaultExchangeConfig() throws UnsupportedConfigurationException;

    /**
     * {@return this policy's view on a proposed {@link KeyExchange} should be executed, if at all possible.
     * @throws UnsupportedConfigurationException if the exchange's execution plan cannot be reliably established
     */
    List<KeyExchangeFactory> exchangeConfigOf(KeyExchange keyExchange) throws UnsupportedConfigurationException;

    // Resolves a potentially absent KeyExchange into a list of
    default List<KeyExchangeFactory> exchangeConfigOrDefault(final @Nullable KeyExchange keyExchange)
            throws UnsupportedConfigurationException {
        return keyExchange == null ? defaultExchangeConfig() : exchangeConfigOf(keyExchange);
    }
}
