/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.crypto;

import java.security.PublicKey;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.PublicKeyFormat;

/**
 * A class capable of creating a {@link PublicKey}s of a particular {@link PublicKeyFormat}.
 */
@NonNullByDefault
public interface PublicKeySupport<F extends PublicKeyFormat> {

    F format();

    PublicKey createKey(String algorithm, byte[] bytes) throws UnsupportedConfigurationException;
}
