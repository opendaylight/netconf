/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.controller.sal.rest.doc.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.OAversion;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.URIType;
import org.opendaylight.netconf.sal.rest.doc.impl.MountPointSwaggerGeneratorDraft02;
import org.opendaylight.netconf.sal.rest.doc.mountpoints.MountPointSwagger;
import org.opendaylight.netconf.sal.rest.doc.swagger.SwaggerObject;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class MountPointSwaggerTest {

    private static final String HTTP_URL = "http://localhost/path";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
            .node(QName.create("", "nodes"))
            .node(QName.create("", "node"))
            .nodeWithKey(QName.create("", "node"), QName.create("", "id"), "123").build();
    private static final String INSTANCE_URL = "/nodes/node/123/";
    private MountPointSwagger swagger;
    private DocGenTestHelper helper;

    @SuppressWarnings("resource")
    @Before
    public void setUp() throws Exception {
        this.helper = new DocGenTestHelper();
        this.helper.setUp();

        // We are sharing the global schema service and the mount schema service
        // in our test.
        // OK for testing - real thing would have separate instances.
        final EffectiveModelContext context = this.helper.createMockSchemaContext();
        final DOMSchemaService schemaService = this.helper.createMockSchemaService(context);

        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(schemaService));

        final DOMMountPointService service = mock(DOMMountPointService.class);
        when(service.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));

        final MountPointSwaggerGeneratorDraft02 generator =
                new MountPointSwaggerGeneratorDraft02(schemaService, service);

        this.swagger = generator.getMountPointSwagger();
    }

    @Test()
    public void getInstanceIdentifiers() throws Exception {
        assertEquals(0, this.swagger.getInstanceIdentifiers().size());
        this.swagger.onMountPointCreated(INSTANCE_ID); // add this ID into the list of mount points
        assertEquals(1, this.swagger.getInstanceIdentifiers().size());
        assertEquals((Long) 1L, this.swagger.getInstanceIdentifiers().entrySet().iterator().next()
                .getValue());
        assertEquals(INSTANCE_URL, this.swagger.getInstanceIdentifiers().entrySet().iterator().next()
                .getKey());
        this.swagger.onMountPointRemoved(INSTANCE_ID); // remove ID from list of mount points
        assertEquals(0, this.swagger.getInstanceIdentifiers().size());
    }

    @Test
    public void testGetDataStoreApi() throws Exception {
        final UriInfo mockInfo = this.helper.createMockUriInfo(HTTP_URL);
        this.swagger.onMountPointCreated(INSTANCE_ID); // add this ID into the list of mount points

        final SwaggerObject mountPointApi = (SwaggerObject) this.swagger.getMountPointApi(mockInfo, 1L, "Datastores",
                "-", URIType.DRAFT02, OAversion.V2_0);
        assertNotNull("failed to find Datastore API", mountPointApi);

        final ObjectNode pathsObject = mountPointApi.getPaths();
        assertNotNull(pathsObject);

        assertEquals("Unexpected api list size", 3, pathsObject.size());

        final Set<String> actualUrls = new TreeSet<>();

        final Iterator<Map.Entry<String, JsonNode>> fields = pathsObject.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> field = fields.next();
            final String path = field.getKey();
            final JsonNode operations = field.getValue();
            actualUrls.add(field.getKey());
            assertEquals("unexpected operations size on " + path, 1, operations.size());

            final JsonNode getOperation = operations.get("get");

            assertNotNull("unexpected operation method on " + path, getOperation);

            assertNotNull("expected non-null desc on " + path, getOperation.get("description"));
        }

        final Set<String> expectedUrls = new TreeSet<>(Arrays.asList(
                "/restconf/config" + INSTANCE_URL + "yang-ext:mount",
                "/restconf/operational" + INSTANCE_URL + "yang-ext:mount",
                "/restconf/operations" + INSTANCE_URL + "yang-ext:mount"));
        assertEquals(expectedUrls, actualUrls);
    }
}
