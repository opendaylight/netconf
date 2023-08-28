/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.jersey.providers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.AbstractInstanceIdentifierTest;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public abstract class AbstractBodyReaderTest extends AbstractInstanceIdentifierTest {
    protected final DatabindProvider databindProvider;
    protected final DOMMountPointService mountPointService;
    protected final DOMMountPoint mountPoint;

    protected AbstractBodyReaderTest() {
        this(IID_SCHEMA);
    }

    protected AbstractBodyReaderTest(final EffectiveModelContext schemaContext) {
        final var databindContext = DatabindContext.ofModel(schemaContext);
        databindProvider = () -> databindContext;

        mountPointService = mock(DOMMountPointService.class);
        mountPoint = mock(DOMMountPoint.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(any(YangInstanceIdentifier.class));
        doReturn(Optional.of(FixedDOMSchemaService.of(schemaContext))).when(mountPoint)
            .getService(DOMSchemaService.class);
    }

    protected static final List<File> loadFiles(final String resourceDirectory) throws FileNotFoundException {
        final String path = AbstractBodyReaderTest.class.getResource(resourceDirectory).getPath();
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
