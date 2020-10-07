/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.FileNotFoundException;
import java.util.Optional;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

public class URITest {

    private static EffectiveModelContext schemaContext;
    private static EffectiveModelContext mountSchemaContext;

    private final DOMMountPoint mountInstance = mock(DOMMountPoint.class);
    private final ControllerContext controllerContext =
            TestRestconfUtils.newControllerContext(schemaContext, mountInstance);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @BeforeClass
    public static void init() throws FileNotFoundException, ReactorException {
        schemaContext = TestUtils.loadSchemaContext("/full-versions/yangs");
        mountSchemaContext = TestUtils.loadSchemaContext("/test-config-data/yang2");
    }

    @Test
    public void testToInstanceIdentifierList() throws FileNotFoundException {
        InstanceIdentifierContext<?> instanceIdentifier = controllerContext
                .toInstanceIdentifier("simple-nodes:userWithoutClass/foo");
        assertEquals(instanceIdentifier.getSchemaNode().getQName().getLocalName(), "userWithoutClass");

        instanceIdentifier = controllerContext.toInstanceIdentifier("simple-nodes:userWithoutClass/foo");
        assertEquals(instanceIdentifier.getSchemaNode().getQName().getLocalName(), "userWithoutClass");

        instanceIdentifier = controllerContext.toInstanceIdentifier("simple-nodes:user/foo/boo");
        assertEquals(instanceIdentifier.getSchemaNode().getQName().getLocalName(), "user");

        instanceIdentifier = controllerContext.toInstanceIdentifier("simple-nodes:user//boo");
        assertEquals(instanceIdentifier.getSchemaNode().getQName().getLocalName(), "user");

    }

    @Test
    public void testToInstanceIdentifierWithDoubleSlash() {
        InstanceIdentifierContext<?> instanceIdentifier = controllerContext
                .toInstanceIdentifier("simple-nodes:food//nonalcoholic");
        assertEquals(instanceIdentifier.getSchemaNode().getQName().getLocalName(), "nonalcoholic");

        instanceIdentifier = controllerContext
                .toInstanceIdentifier("simple-nodes:userWithoutClass//");
        assertEquals(instanceIdentifier.getSchemaNode().getQName().getLocalName(), "userWithoutClass");

        instanceIdentifier = controllerContext
                .toInstanceIdentifier("simple-nodes:userWithoutClass///inner-container");
        assertEquals(instanceIdentifier.getSchemaNode().getQName().getLocalName(), "inner-container");
    }

    @Test
    public void testToInstanceIdentifierListWithNullKey() {
        this.exception.expect(RestconfDocumentedException.class);
        controllerContext.toInstanceIdentifier("simple-nodes:user/null/boo");
    }

    @Test
    public void testToInstanceIdentifierListWithMissingKey() {
        this.exception.expect(RestconfDocumentedException.class);
        controllerContext.toInstanceIdentifier("simple-nodes:user/foo");
    }

    @Test
    public void testToInstanceIdentifierContainer() throws FileNotFoundException {
        final InstanceIdentifierContext<?> instanceIdentifier =
                controllerContext.toInstanceIdentifier("simple-nodes:users");
        assertEquals(instanceIdentifier.getSchemaNode().getQName().getLocalName(), "users");
        assertTrue(instanceIdentifier.getSchemaNode() instanceof ContainerSchemaNode);
        assertEquals(2, ((ContainerSchemaNode) instanceIdentifier.getSchemaNode()).getChildNodes().size());
    }

    @Test
    @Ignore //jenkins has problem with JerseyTest
    // - we expecting problems with singletons ControllerContext as schemaContext holder
    public void testToInstanceIdentifierChoice() throws FileNotFoundException {
        final InstanceIdentifierContext<?> instanceIdentifier = controllerContext
                .toInstanceIdentifier("simple-nodes:food/nonalcoholic");
        assertEquals(instanceIdentifier.getSchemaNode().getQName().getLocalName(), "nonalcoholic");
    }

    @Test
    public void testToInstanceIdentifierChoiceException() {
        this.exception.expect(RestconfDocumentedException.class);
        controllerContext.toInstanceIdentifier("simple-nodes:food/snack");
    }

    @Test
    public void testToInstanceIdentifierCaseException() {
        this.exception.expect(RestconfDocumentedException.class);
        controllerContext.toInstanceIdentifier("simple-nodes:food/sports-arena");
    }

    @Test
    public void testToInstanceIdentifierChoiceCaseException() {
        this.exception.expect(RestconfDocumentedException.class);
        controllerContext.toInstanceIdentifier("simple-nodes:food/snack/sports-arena");
    }

    @Test
    public void testToInstanceIdentifierWithoutNode() {
        this.exception.expect(RestconfDocumentedException.class);
        controllerContext.toInstanceIdentifier("simple-nodes");
    }

    @Test
    public void testMountPointWithExternModul() throws FileNotFoundException, ReactorException {
        initSchemaService();
        final InstanceIdentifierContext<?> instanceIdentifier = controllerContext
                .toInstanceIdentifier("simple-nodes:users/yang-ext:mount/test-interface2:class/student/name");
        assertEquals(
                "[(urn:ietf:params:xml:ns:yang:test-interface2?revision=2014-08-01)class, "
                        + "(urn:ietf:params:xml:ns:yang:test-interface2?revision=2014-08-01)student, "
                        + "(urn:ietf:params:xml:ns:yang:test-interface2?revision=2014-08-01)student"
                        + "[{(urn:ietf:params:xml:ns:yang:test-interface2?revision=2014-08-01)name=name}]]",
                ImmutableList.copyOf(instanceIdentifier.getInstanceIdentifier().getPathArguments()).toString());
    }

    @Test
    public void testMountPointWithoutExternModul() throws FileNotFoundException, ReactorException {
        initSchemaService();
        final InstanceIdentifierContext<?> instanceIdentifier = controllerContext
                .toInstanceIdentifier("simple-nodes:users/yang-ext:mount/");
        assertTrue(Iterables.isEmpty(instanceIdentifier.getInstanceIdentifier().getPathArguments()));
    }

    @Test
    public void testMountPointWithoutMountPointSchema() throws FileNotFoundException, ReactorException {
        this.exception.expect(RestconfDocumentedException.class);

        controllerContext.toInstanceIdentifier("simple-nodes:users/yang-ext:mount/test-interface2:class");
    }

    public void initSchemaService() {
        doReturn(Optional.of(FixedDOMSchemaService.of(mountSchemaContext))).when(mountInstance)
            .getService(DOMSchemaService.class);
    }
}
