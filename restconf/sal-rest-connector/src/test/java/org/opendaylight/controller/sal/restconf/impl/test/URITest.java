/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.FileNotFoundException;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfImpl;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;

public class URITest {

    private static final ControllerContext CONTROLLER_CONTEXT = ControllerContext.getInstance();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @BeforeClass
    public static void init() throws FileNotFoundException, ReactorException {
        final SchemaContext schemaContext = TestUtils.loadSchemaContext("/full-versions/yangs");
        final Set<Module> allModules = schemaContext.getModules();
        assertNotNull(allModules);

        CONTROLLER_CONTEXT.setSchemas(schemaContext);
    }

    @Test
    public void testToInstanceIdentifierList() throws FileNotFoundException {
        InstanceIdentifierContext<?> instanceIdentifier = CONTROLLER_CONTEXT
                .toInstanceIdentifier("simple-nodes:userWithoutClass/foo");
        assertEquals(instanceIdentifier.getSchemaNode().getQName().getLocalName(), "userWithoutClass");

        instanceIdentifier = CONTROLLER_CONTEXT.toInstanceIdentifier("simple-nodes:userWithoutClass/foo");
        assertEquals(instanceIdentifier.getSchemaNode().getQName().getLocalName(), "userWithoutClass");

        instanceIdentifier = CONTROLLER_CONTEXT.toInstanceIdentifier("simple-nodes:user/foo/boo");
        assertEquals(instanceIdentifier.getSchemaNode().getQName().getLocalName(), "user");

        instanceIdentifier = CONTROLLER_CONTEXT.toInstanceIdentifier("simple-nodes:user//boo");
        assertEquals(instanceIdentifier.getSchemaNode().getQName().getLocalName(), "user");

    }

    @Test
    public void testToInstanceIdentifierListWithNullKey() {
        this.exception.expect(RestconfDocumentedException.class);
        CONTROLLER_CONTEXT.toInstanceIdentifier("simple-nodes:user/null/boo");
    }

    @Test
    public void testToInstanceIdentifierListWithMissingKey() {
        this.exception.expect(RestconfDocumentedException.class);
        CONTROLLER_CONTEXT.toInstanceIdentifier("simple-nodes:user/foo");
    }

    @Test
    public void testToInstanceIdentifierContainer() throws FileNotFoundException {
        final InstanceIdentifierContext<?> instanceIdentifier =
                CONTROLLER_CONTEXT.toInstanceIdentifier("simple-nodes:users");
        assertEquals(instanceIdentifier.getSchemaNode().getQName().getLocalName(), "users");
        assertTrue(instanceIdentifier.getSchemaNode() instanceof ContainerSchemaNode);
        assertEquals(2, ((ContainerSchemaNode) instanceIdentifier.getSchemaNode()).getChildNodes().size());
    }

    @Test
    @Ignore //jenkins has problem with JerseyTest
    // - we expecting problems with singletons ControllerContext as schemaContext holder
    public void testToInstanceIdentifierChoice() throws FileNotFoundException {
        final InstanceIdentifierContext<?> instanceIdentifier = CONTROLLER_CONTEXT
                .toInstanceIdentifier("simple-nodes:food/nonalcoholic");
        assertEquals(instanceIdentifier.getSchemaNode().getQName().getLocalName(), "nonalcoholic");
    }

    @Test
    public void testToInstanceIdentifierChoiceException() {
        this.exception.expect(RestconfDocumentedException.class);
        CONTROLLER_CONTEXT.toInstanceIdentifier("simple-nodes:food/snack");
    }

    @Test
    public void testToInstanceIdentifierCaseException() {
        this.exception.expect(RestconfDocumentedException.class);
        CONTROLLER_CONTEXT.toInstanceIdentifier("simple-nodes:food/sports-arena");
    }

    @Test
    public void testToInstanceIdentifierChoiceCaseException() {
        this.exception.expect(RestconfDocumentedException.class);
        CONTROLLER_CONTEXT.toInstanceIdentifier("simple-nodes:food/snack/sports-arena");
    }

    @Test
    public void testToInstanceIdentifierWithoutNode() {
        this.exception.expect(RestconfDocumentedException.class);
        CONTROLLER_CONTEXT.toInstanceIdentifier("simple-nodes");
    }

    @Test
    public void testMountPointWithExternModul() throws FileNotFoundException, ReactorException {
        initMountService(true);
        final InstanceIdentifierContext<?> instanceIdentifier = CONTROLLER_CONTEXT
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
        initMountService(true);
        final InstanceIdentifierContext<?> instanceIdentifier = CONTROLLER_CONTEXT
                .toInstanceIdentifier("simple-nodes:users/yang-ext:mount/");
        assertTrue(Iterables.isEmpty(instanceIdentifier.getInstanceIdentifier().getPathArguments()));
    }

    @Test
    public void testMountPointWithoutMountService() throws FileNotFoundException {
        this.exception.expect(RestconfDocumentedException.class);

        CONTROLLER_CONTEXT.setMountService(null);
        CONTROLLER_CONTEXT.toInstanceIdentifier("simple-nodes:users/yang-ext:mount/test-interface2:class/student/name");
    }

    @Test
    public void testMountPointWithoutMountPointSchema() throws FileNotFoundException, ReactorException {
        initMountService(false);
        this.exception.expect(RestconfDocumentedException.class);

        CONTROLLER_CONTEXT.toInstanceIdentifier("simple-nodes:users/yang-ext:mount/test-interface2:class");
    }

    public void initMountService(final boolean withSchema) throws FileNotFoundException, ReactorException {
        final DOMMountPointService mountService = mock(DOMMountPointService.class);
        CONTROLLER_CONTEXT.setMountService(mountService);
        final BrokerFacade brokerFacade = mock(BrokerFacade.class);
        final RestconfImpl restconfImpl = RestconfImpl.getInstance();
        restconfImpl.setBroker(brokerFacade);
        restconfImpl.setControllerContext(CONTROLLER_CONTEXT);
        final SchemaContext schemaContext2 = TestUtils.loadSchemaContext("/test-config-data/yang2");
        final Set<Module> modules2 = schemaContext2.getModules();

        final DOMMountPoint mountInstance = mock(DOMMountPoint.class);
        if (withSchema) {
            when(mountInstance.getSchemaContext()).thenReturn(schemaContext2);
        } else {
            when(mountInstance.getSchemaContext()).thenReturn(null);
        }
        when(mountService.getMountPoint(any(YangInstanceIdentifier.class))).thenReturn(Optional.of(mountInstance));
    }
}
