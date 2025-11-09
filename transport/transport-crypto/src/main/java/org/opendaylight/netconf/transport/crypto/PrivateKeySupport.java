/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.crypto;

import java.security.PrivateKey;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.PrivateKeyFormat;

/**
 * A class capable of creating a {@link PrivateKey}s of a particular {@link PrivateKeyFormat}.
 */
// FIXME: expose this interface once we have Dagger available
@NonNullByDefault
interface PrivateKeySupport<F extends PrivateKeyFormat> {

    F format();

    PrivateKey createKey(byte[] bytes) throws UnsupportedConfigurationException;
}
