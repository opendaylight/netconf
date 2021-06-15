/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TestRestconfUtils {

    private static final Logger LOG = LoggerFactory.getLogger(TestRestconfUtils.class);

    private TestRestconfUtils() {
        throw new UnsupportedOperationException("Test utility class");
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static EffectiveModelContext loadSchemaContext(final String yangPath,
            final EffectiveModelContext schemaContext) {
        try {
            Preconditions.checkArgument(yangPath != null, "Path can not be null.");
            Preconditions.checkArgument(!yangPath.isEmpty(), "Path can not be empty.");
            if (schemaContext == null) {
                return YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles(yangPath));
            } else {
                throw new UnsupportedOperationException("Unable to add new yang sources to existing schema context.");
            }
        } catch (final Exception e) {
            LOG.error("Yang files at path: " + yangPath + " weren't loaded.", e);
        }
        return schemaContext;
    }

    public static Collection<File> loadFiles(final String resourceDirectory) throws FileNotFoundException {
        final String path = TestRestconfUtils.class.getResource(resourceDirectory).getPath();
        final File testDir = new File(path);
        final String[] fileList = testDir.list();
        final List<File> testFiles = new ArrayList<>();
        if (fileList == null) {
            throw new FileNotFoundException(resourceDirectory);
        }
        for (final String fileName : fileList) {
            if (new File(testDir, fileName).isDirectory() == false) {
                testFiles.add(new File(testDir, fileName));
            }
        }
        return testFiles;
    }
}
