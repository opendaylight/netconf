/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.keystore.rev171017.keystore.entry.KeyCredential;

public interface CredentialProvider {
    /**
     * Get the a {@link KeyCredential} for a particular id.
     *
     * @param id Credential id
     * @return A {@link KeyCredential} object, {@code null} if not found
     * @throws NullPointerException if {@code id} is {@code null}
     */
    @Nullable KeyCredential credentialForId(String id);
}
