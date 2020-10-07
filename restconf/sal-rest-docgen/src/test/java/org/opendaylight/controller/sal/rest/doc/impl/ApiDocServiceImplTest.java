/*
 * Copyright (c) 2018 ZTE Corporation and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.rest.doc.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import javax.ws.rs.core.UriInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.doc.api.ApiDocService;
import org.opendaylight.netconf.sal.rest.doc.impl.AllModulesDocGenerator;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocGeneratorDraftO2;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocGeneratorRFC8040;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl;
import org.opendaylight.netconf.sal.rest.doc.impl.MountPointSwaggerGeneratorDraft02;
import org.opendaylight.netconf.sal.rest.doc.impl.MountPointSwaggerGeneratorRFC8040;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class ApiDocServiceImplTest {
    private static final String HTTP_URL = "http://localhost/path";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
            .node(QName.create("", "nodes"))
            .node(QName.create("", "node"))
            .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();
    private DocGenTestHelper helper;
    private ApiDocService apiDocService;


    @SuppressWarnings("resource")
    @Before
    public void setUp() throws Exception {
        this.helper = new DocGenTestHelper();
        this.helper.setUp();

        final EffectiveModelContext context = this.helper.createMockSchemaContext();
        final DOMSchemaService schemaService = this.helper.createMockSchemaService(context);

        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(schemaService));

        final DOMMountPointService service = mock(DOMMountPointService.class);
        when(service.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));
        final MountPointSwaggerGeneratorDraft02 mountPointDraft02 =
                new MountPointSwaggerGeneratorDraft02(schemaService, service);
        final MountPointSwaggerGeneratorRFC8040 mountPointRFC8040 =
                new MountPointSwaggerGeneratorRFC8040(schemaService, service);
        final ApiDocGeneratorDraftO2 apiDocGeneratorDraftO2 = new ApiDocGeneratorDraftO2(schemaService);
        final ApiDocGeneratorRFC8040 apiDocGeneratorRFC8040 = new ApiDocGeneratorRFC8040(schemaService);
        mountPointDraft02.getMountPointSwagger().onMountPointCreated(INSTANCE_ID);
        final AllModulesDocGenerator allModulesDocGenerator = new AllModulesDocGenerator(apiDocGeneratorDraftO2,
                apiDocGeneratorRFC8040);
        this.apiDocService = new ApiDocServiceImpl(mountPointDraft02, mountPointRFC8040, apiDocGeneratorDraftO2,
                apiDocGeneratorRFC8040, allModulesDocGenerator);
    }

    @Test
    public void getListOfMounts() throws java.net.URISyntaxException, JsonProcessingException {
        final UriInfo mockInfo = this.helper.createMockUriInfo(HTTP_URL);
        // simulate the behavior of JacksonJaxbJsonProvider
        final ObjectMapper mapper = new ObjectMapper();
        final String result = mapper.writer().writeValueAsString(
                apiDocService.getListOfMounts(mockInfo).getEntity());
        Assert.assertEquals("[{\"instance\":\"/nodes/node/123/\",\"id\":1}]", result);
    }
}
