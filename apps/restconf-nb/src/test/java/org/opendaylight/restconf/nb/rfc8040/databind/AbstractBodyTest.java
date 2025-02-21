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
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.restconf.server.api.testlib.AbstractInstanceIdentifierTest;
import org.opendaylight.yangtools.yang.common.YangConstants;

public abstract class AbstractBodyTest extends AbstractInstanceIdentifierTest {
    static final List<File> loadFiles(final String resourceDirectory) throws FileNotFoundException {
        final String path = requireNonNull(AbstractBodyTest.class.getResource(resourceDirectory), resourceDirectory)
            .getPath();
        final File testDir = new File(path);
        final String[] fileList = testDir.list();
        final List<File> testFiles = new ArrayList<>();
        if (fileList == null) {
            throw new FileNotFoundException(resourceDirectory);
        }
        for (final String fileName : fileList) {
            if (fileName.endsWith(YangConstants.RFC6020_YANG_FILE_EXTENSION)
                && !new File(testDir, fileName).isDirectory()) {
                testFiles.add(new File(testDir, fileName));
            }
        }
        return testFiles;
    }
}
