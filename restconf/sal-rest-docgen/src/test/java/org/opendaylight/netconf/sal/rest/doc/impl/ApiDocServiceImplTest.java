/*
 * Copyright (c) 2018 ZTE Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.doc.api.ApiDocService;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public final class ApiDocServiceImplTest extends AbstractApiDocTest {
    private static final String HTTP_URL = "http://localhost/path";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
            .node(QName.create("", "nodes"))
            .node(QName.create("", "node"))
            .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ApiDocService apiDocService;

    @Before
    public void before() {
        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(SCHEMA_SERVICE));

        final DOMMountPointService service = mock(DOMMountPointService.class);
        when(service.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));
        final MountPointSwaggerGeneratorRFC8040 mountPointRFC8040 =
                new MountPointSwaggerGeneratorRFC8040(SCHEMA_SERVICE, service);
        final ApiDocGeneratorRFC8040 apiDocGeneratorRFC8040 = new ApiDocGeneratorRFC8040(SCHEMA_SERVICE);
        mountPointRFC8040.getMountPointSwagger().onMountPointCreated(INSTANCE_ID);
        final AllModulesDocGenerator allModulesDocGenerator = new AllModulesDocGenerator(apiDocGeneratorRFC8040);
        apiDocService = new ApiDocServiceImpl(mountPointRFC8040, apiDocGeneratorRFC8040, allModulesDocGenerator);
    }

    @Test
    public void getListOfMounts() throws Exception {
        final UriInfo mockInfo = DocGenTestHelper.createMockUriInfo(HTTP_URL);
        // simulate the behavior of JacksonJaxbJsonProvider
        final String result = MAPPER.writer().writeValueAsString(apiDocService.getListOfMounts(mockInfo).getEntity());
        assertEquals("[{\"instance\":\"/nodes/node=123/\",\"id\":1}]", result);
    }
}
