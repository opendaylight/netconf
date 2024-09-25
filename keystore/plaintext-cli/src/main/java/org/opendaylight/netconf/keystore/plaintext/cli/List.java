/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.keystore.plaintext.cli;

import java.util.ArrayList;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;

@Service
@Command(scope = AbstractCommand.SCOPE, name = "list", description = "Lists property names")
public class List extends AbstractCommand {

    @Override
    @SuppressWarnings("RegexpSinglelineJava")
    final void executeCommand() {
        final var unsorted = new ArrayList<String>();
        for (var entry : storage) {
            unsorted.add(toString(entry.getKey()));
        }
        if (unsorted.isEmpty()) {
            System.out.println("Storage is empty");
        } else {
            unsorted.stream().sorted(String::compareToIgnoreCase).forEach(System.out::println);
        }
    }
}
