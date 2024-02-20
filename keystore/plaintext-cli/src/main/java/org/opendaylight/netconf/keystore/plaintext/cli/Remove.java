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
@Command(scope = AbstractCommand.SCOPE, name = "remove", description = "Removes property by name")
public class Remove extends AbstractCommand {

    @Argument(name = "name", required = true)
    String key;

    @Override
    @SuppressWarnings("RegexpSinglelineJava")
    void executeCommand() throws IOException {
        final var result = storage.removeKey(toBytes(key));
        System.out.println(result == null ? notFound(key) : "Property removed");
    }
}
