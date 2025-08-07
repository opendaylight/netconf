/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.opendaylight.restconf.server.api.testlib.AbstractInstanceIdentifierTest;
import org.opendaylight.yangtools.yang.common.YangConstants;

abstract class AbstractBodyTest extends AbstractInstanceIdentifierTest {
    static final List<File> loadFiles(final String resourceDirectory) {
        final var dirURL = requireNonNull(AbstractBodyTest.class.getResource(resourceDirectory), resourceDirectory);
        try (var files = Files.list(Path.of(dirURL.toURI()))) {
            return files
                .filter(file -> file.toString().endsWith(YangConstants.RFC6020_YANG_FILE_EXTENSION))
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .collect(Collectors.toList());
        } catch (IOException | URISyntaxException e) {
            throw new AssertionError(e);
        }
    }
}
