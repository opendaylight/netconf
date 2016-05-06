/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.services;

import org.junit.Test;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.rest.api.services.RestconfModulesService;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;

/**
 * Tests of the {@link RestconfModulesService}
 *
 */
public class RestconfModulesServiceTest {

    // POSITIVE TESTS

    /**
     * Test non-null init of {@link RestconfModulesServiceImpl}.
     */
    @Test
    public void restconfModulesServiceImpl() {

    }

    /**
     * Test getting all modules supported by the server.
     */
    @Test
    public void getModulesTest() {

    }

    /**
     * Test getting all modules supported by the mount point.
     */
    @Test
    public void getModulesFromMountPointTest() {

    }

    /**
     * Test getting the specific module supported by the server.
     */
    @Test
    public void getModuleTest() {

    }

    /**
     * Test getting the specific module supported by the mount point.
     */
    @Test
    public void getModuleFromMountPointTest() {

    }

    // NEGATIVE TESTS

    /**
     * Test getting all modules supported by the server if restconf modules does
     * not exit.
     */
    @Test(expected = NullPointerException.class)
    public void getModulesWithoutRestconfModule() {

    }

    /**
     * Test getting all modules supported by the server - restconf module is not
     * {@link ContainerSchemaNode}
     */
    @Test(expected = IllegalStateException.class)
    public void getModulesRestconfModuleWithoutContSchemaNode() {

    }

    /**
     * Test getting all modules supported by the mount point with null value of
     * identifier for mount point.
     */
    @Test(expected = NullPointerException.class)
    public void getModulesFromMountPointWithNullIdentifier() {

    }

    /**
     * Test getting all modules supported by the mount point if identifier does
     * not contains {@link RestconfConstants#MOUNT}. Catching
     * {@link RestconfDocumentedException} and testing {@link ErrorType} and
     * {@link ErrorTag}.
     */
    @Test
    public void getModulesFromMountPointWithoutMountIdentifierOnEndPathIdentifier() {

    }

    /**
     * Test getting all modules supported by the mount point if restconf modules
     * does not exit.
     */
    @Test(expected = NullPointerException.class)
    public void getModulesFromMountPointWithoutRestconfModule() {

    }

    /**
     * Test getting all modules supported by the mount point - restconf module
     * is not {@link ContainerSchemaNode}
     */
    @Test(expected = IllegalStateException.class)
    public void getModulesFromMountPointRestconfModuleWithoutContSchemaNode() {

    }

    /**
     * Testing getting specific module supported by the server with null
     * identifier param.
     */
    @Test(expected = NullPointerException.class)
    public void getModuleWithNullIdentifier() {

    }

    /**
     * Testing getting specific module supported by the server with module which
     * does not exist in schema. Catching {@link RestconfDocumentedException}
     * and testing {@link ErrorType} and {@link ErrorTag}.
     */
    @Test
    public void getModuleModuleDoesNotExist() {

    }

    /**
     * Test getting specific module supported by the server - restconf module is
     * not {@link ListSchemaNode}.
     */
    @Test(expected = IllegalStateException.class)
    public void getModuleRestconfModuleWithoutListSchemaNode() {

    }

    /**
     * Testing getting specific module supported by the mount point with module
     * which does not exist in schema. Catching
     * {@link RestconfDocumentedException} and testing {@link ErrorType} and
     * {@link ErrorTag}.
     */
    @Test
    public void getModuleFromMountPointModuleDoesNotExist() {

    }

    /**
     * Test getting specific module supported by the mount point - restconf
     * module is not {@link ListSchemaNode}.
     */
    @Test(expected = IllegalStateException.class)
    public void getModuleFromMountPointRestconfModuleWithoutListSchemaNode() {

    }
}
