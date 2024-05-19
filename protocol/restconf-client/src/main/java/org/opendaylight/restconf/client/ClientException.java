/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.client;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.rev170126.YangErrors;

/**
 * A RESTCONF failure described by {@link YangErrors}.
 */
@NonNullByDefault
public final class ClientException extends Exception {
    @java.io.Serial
    private static final long serialVersionUID = 0L;

    private final YangErrors yangErrors;

    public ClientException(final YangErrors yangErrors) {
        this.yangErrors = requireNonNull(yangErrors);
    }

    /**
     * Return the errors causing this exception.
     *
     * @return the errors causing this exception
     */
    public YangErrors yangErrors() {
        return yangErrors;
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
