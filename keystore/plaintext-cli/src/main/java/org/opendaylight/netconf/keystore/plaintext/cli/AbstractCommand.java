/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.plaintext.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.opendaylight.netconf.keystore.plaintext.api.MutablePlaintextStorage;

abstract class AbstractCommand implements Action {
    static final String SCOPE = "odl-plaintext-storage";

    @Reference
    MutablePlaintextStorage storage;

    @Override
    public Object execute() throws Exception {
        if (storage != null) {
            executeCommand();
        }
        return null;
    }

    abstract void executeCommand() throws IOException;

    protected static String toString(final byte[] bytes) {
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    protected static byte[] toBytes(final String string) {
        return string == null ? new byte[0] : string.getBytes(StandardCharsets.UTF_8);
    }

    protected static String notFound(final String key) {
        return String.format("Storage has no property with name %s", key);
    }
}

