/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.plaintext.cli;

import java.io.IOException;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;

@Service
@Command(scope = AbstractCommand.SCOPE, name = "put",
    description = "Puts property into storage. Overrides the value if there is a property with same name")
public class Put extends AbstractCommand {

    @Argument(name = "name", required = true)
    String key;

    @Argument(name = "value", required = true, index = 1)
    String value;

    @Override
    @SuppressWarnings("RegexpSinglelineJava")
    void executeCommand() throws IOException {
        final var result = storage.putEntry(toBytes(key), toBytes(value));
        System.out.println(result == null ? "Property added" :
            String.format("Property value updated. Previous value: %s", toString(result)));
    }
}
