/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.server;

import org.opendaylight.aaa.encrypt.AAAEncryptionService;

/**
 * An {@link AAAEncryptionService} which performs no encryption at all.
 */
public final class NullAAAEncryptionService implements AAAEncryptionService {
    @Override
    public byte[] encrypt(final byte[] data) {
        return data;
    }

    @Override
    public byte[] decrypt(final byte[] encryptedData) {
        return encryptedData;
    }
}