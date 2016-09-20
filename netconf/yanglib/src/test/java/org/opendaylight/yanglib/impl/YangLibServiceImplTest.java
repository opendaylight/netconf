/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.yanglib.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(SharedSchemaRepository.class)
public class YangLibServiceImplTest {

    @Test
    public void testSchema() throws SchemaSourceException {

        final SharedSchemaRepository schemaRepository = PowerMockito.mock(SharedSchemaRepository.class);
        final YangLibServiceImpl yangLibService = new YangLibServiceImpl();
        yangLibService.setSchemaRepository(schemaRepository);

        final SourceIdentifier sourceIdentifier = RevisionSourceIdentifier.create("name", "2016-01-01");

        final YangTextSchemaSource yangTextSchemaSource = new YangTextSchemaSource(sourceIdentifier) {
            @Override
            protected MoreObjects.ToStringHelper addToStringAttributes(MoreObjects.ToStringHelper toStringHelper) {
                return null;
            }

            @Override
            public InputStream openStream() throws IOException {
                return new ByteArrayInputStream(new byte[]{104, 101, 108, 112});
            }
        };

        final CheckedFuture<YangTextSchemaSource, SchemaSourceException> sourceFuture =
                Futures.immediateCheckedFuture(yangTextSchemaSource);
        doReturn(sourceFuture).when(schemaRepository).getSchemaSource(any(SourceIdentifier.class), eq(YangTextSchemaSource.class));

        final String outputStream = yangLibService.getSchema("name", "2016-01-01");
        assertEquals("help", outputStream);
    }

}
