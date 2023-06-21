/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.client;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import java.io.Serial;
import org.opendaylight.netconf.shaded.sshd.common.SshException;

/**
 * Exception reported when endpoint authentication fails.
 */
@Beta
public class AuthenticationFailedException extends SshException {
    @Serial
    private static final long serialVersionUID = 1L;

    public AuthenticationFailedException(final String message) {
        super(requireNonNull(message));
    }

    public AuthenticationFailedException(final String message, final Throwable cause) {
        super(requireNonNull(message), cause);
    }
}
