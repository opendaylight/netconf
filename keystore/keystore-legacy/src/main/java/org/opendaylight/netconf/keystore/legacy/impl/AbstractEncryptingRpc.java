/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.legacy.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import java.security.GeneralSecurityException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

/**
 * An {@link AbstractRpc} bound to an {@link AAAEncryptionService} instance.
 */
abstract class AbstractEncryptingRpc extends AbstractRpc {
    private final AAAEncryptionService encryptionService;

    AbstractEncryptingRpc(final DataBroker dataBroker, final AAAEncryptionService encryptionService) {
        super(dataBroker);
        this.encryptionService = requireNonNull(encryptionService);
    }

    final byte @NonNull [] encryptEncoded(final byte[] bytes) throws GeneralSecurityException {
        return encryptionService.encrypt(bytes);
    }

    static final <T> @NonNull ListenableFuture<RpcResult<T>> returnFailed(final String message, final Exception cause) {
        return RpcResultBuilder.<T>failed().withError(ErrorType.APPLICATION, message, cause).buildFuture();
    }
}
