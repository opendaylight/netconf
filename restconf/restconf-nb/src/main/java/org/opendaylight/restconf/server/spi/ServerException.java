/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

/**
 * A server-side processing exception. This exception is not serializable on purpose.
 */
public final class ServerException extends Exception {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final @NonNull RestconfError error;

    public ServerException(final String message) {
        this(new RestconfError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, requireNonNull(message), null, null,
            null));
    }

    public ServerException(final String message, final Throwable cause) {
        this(new RestconfError(ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, requireNonNull(message), null,
            cause.getMessage(), null));
    }

    public ServerException(final RestconfError error) {
        super(error.getErrorMessage());
        this.error = error;
    }

    public ServerException(final RestconfError error, final Throwable cause) {
        super(error.getErrorMessage(), cause);
        this.error = error;
    }

    public @NonNull RestconfError error() {
        return error;
    }

    @java.io.Serial
    private void readObjectNoData() throws ObjectStreamException {
        throw new NotSerializableException();
    }

    @java.io.Serial
    private void readObject(final ObjectInputStream stream) throws IOException, ClassNotFoundException {
        throw new NotSerializableException();
    }

    @java.io.Serial
    private void writeObject(final ObjectOutputStream stream) throws IOException {
        throw new NotSerializableException();
    }
}
