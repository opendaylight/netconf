/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.mountpoints;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.DocGenTestHelper;
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.opendaylight.restconf.openapi.impl.MountPointOpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.impl.OpenApiGeneratorRFC8040;
import org.opendaylight.restconf.openapi.impl.OpenApiServiceImpl;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Threads(1)
public class MountDocBenchmarkTest {
    private static final String HTTP_URL = "http://localhost/path";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
            .node(QName.create("", "nodes"))
            .node(QName.create("", "node"))
            .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();

    private OpenApiService openApiService;
    private UriInfo mockUriInfo;

    @Param({"10", "100", "1000"})
    public int iterations;

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @Fork(warmups = 1, value = 1)
    @Warmup(batchSize = -1, iterations = 3, time = 10, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(batchSize = -1, iterations = 10, time = 10, timeUnit = TimeUnit.MILLISECONDS)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void test() throws Exception {
        Thread.sleep(ThreadLocalRandom.current().nextInt(0, iterations));
    }

    @Before
    public void before() throws Exception {
        final var mockSchemaService = mock(DOMSchemaService.class);
        final var mockMountPoint = mock(DOMMountPoint.class);
        when(mockMountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(mockSchemaService));
        final var mockMountPointService = mock(DOMMountPointService.class);
        when(mockMountPointService.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mockMountPoint));
        mockUriInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        when(mockUriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
        final var context = YangParserTestUtils.parseYangResourceDirectory("/juniper");
        when(mockSchemaService.getGlobalContext()).thenReturn(context);

        final var mountPointRFC8040 = new MountPointOpenApiGeneratorRFC8040(mockSchemaService, mockMountPointService);
        final var apiDocGeneratorRFC8040 = new OpenApiGeneratorRFC8040(mockSchemaService);
        mountPointRFC8040.getMountPointOpenApi().onMountPointCreated(INSTANCE_ID);
        openApiService = new OpenApiServiceImpl(mountPointRFC8040, apiDocGeneratorRFC8040);
    }

    @Test
    public void getMountDocTest() throws Exception {
        org.openjdk.jmh.Main.main(new String[]{});

        final var response = openApiService.getMountDoc("1", mockUriInfo);
        assertNotNull(response);
    }
}
