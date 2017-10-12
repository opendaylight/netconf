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
import static org.mockito.Mockito.mock;

import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;

public class YangLibServiceImplTest {

    private static final String TEST_OUTPUT_STRING = "hello world";

    @Test
    public void testSchema() throws SchemaSourceException {

        final SchemaRepository schemaRepository = mock(SchemaRepository.class);
        final YangLibServiceImpl yangLibService = new YangLibServiceImpl();
        yangLibService.setSchemaRepository(schemaRepository);

        final SourceIdentifier sourceIdentifier = RevisionSourceIdentifier.create("name", Revision.of("2016-01-01"));

        final YangTextSchemaSource yangTextSchemaSource = new YangTextSchemaSource(sourceIdentifier) {
            @Override
            protected ToStringHelper addToStringAttributes(final ToStringHelper toStringHelper) {
                return null;
            }

            @Override
            public InputStream openStream() throws IOException {
                return new ByteArrayInputStream(TEST_OUTPUT_STRING.getBytes());
            }
        };

        final ListenableFuture<YangTextSchemaSource> sourceFuture = Futures.immediateFuture(yangTextSchemaSource);
        doReturn(sourceFuture).when(schemaRepository).getSchemaSource(any(SourceIdentifier.class),
                eq(YangTextSchemaSource.class));

        final String outputStream = yangLibService.getSchema("name", "2016-01-01");
        assertEquals(TEST_OUTPUT_STRING, outputStream);
    }

}
