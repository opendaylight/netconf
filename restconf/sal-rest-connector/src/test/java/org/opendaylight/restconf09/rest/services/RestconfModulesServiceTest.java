/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf09.rest.services;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.rest.api.Draft09;
import org.opendaylight.restconf.rest.api.connector.RestSchemaController;
import org.opendaylight.restconf.rest.api.services.RestconfModulesService;
import org.opendaylight.restconf.rest.impl.services.RestconfModulesServiceImpl;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

@RunWith(MockitoJUnitRunner.class)
public class RestconfModulesServiceTest {

    private static final String MODUL_IDENTIFIER = "module/2016-03-09";

    private QName MODUL_QNAME;
    private static final QName MODUL = QName.create("module", "2016-03-09", "module");
    private static final QName MODUL2 = QName.create("module2", "2016-03-09", "module2");;

    private static final QName QNAME_CONT = QName.create("cont", "2016-03-09", "module");
    private static final QName QNAME_CHILD_NAME = QName.create("name", "2016-03-09", "name");
    private static final QName QNAME_CHILD_REVISION = QName.create("revision", "2016-03-09", "revision");
    private static final QName QNAME_CHILD_NAMESPACE = QName.create("namespace", "2016-03-09", "namespace");
    private static final QName QNAME_CHILD_FEATURE = QName.create("feature", "2016-03-09", "feature");

    private RestconfModulesService service;

    @Mock
    RestSchemaController schemaController;

    @Before
    public void setup() throws Exception {
        this.service = new RestconfModulesServiceImpl(this.schemaController);
        Assert.assertNotNull(this.service);

        this.MODUL_QNAME = QName.create(null, new SimpleDateFormat("yyyy-MM-dd").parse("2016-03-09"), "module");

        final SchemaContext schemaContext = Mockito.mock(SchemaContext.class);
        Mockito.when(this.schemaController.getGlobalSchema()).thenReturn(schemaContext);

        final Module restconfModule = Mockito.mock(Module.class);
        Mockito.when(this.schemaController.getRestconfModule()).thenReturn(restconfModule);

        final ListSchemaNode dataSchemaNode = Mockito.mock(ListSchemaNode.class);
        Mockito.when(this.schemaController.getRestconfModuleRestConfSchemaNode(restconfModule,
                Draft09.RestConfModule.MODULE_LIST_SCHEMA_NODE)).thenReturn(dataSchemaNode);
        Mockito.when(dataSchemaNode.getQName()).thenReturn(QNAME_CHILD_NAME);

        final List<DataSchemaNode> listChilds = new ArrayList<>();

        final LeafSchemaNode childDataSchemaNode = Mockito.mock(LeafSchemaNode.class);
        listChilds.add(childDataSchemaNode);
        final LeafSchemaNode childDataSchemaNodeRevision = Mockito.mock(LeafSchemaNode.class);
        listChilds.add(childDataSchemaNodeRevision);
        final LeafSchemaNode childDataSchemaNodeNamespace = Mockito.mock(LeafSchemaNode.class);
        listChilds.add(childDataSchemaNodeNamespace);
        final LeafListSchemaNode childDataSchemaNodeFeature = Mockito.mock(LeafListSchemaNode.class);
        listChilds.add(childDataSchemaNodeFeature);

        Mockito.when(childDataSchemaNode.getQName()).thenReturn(QNAME_CHILD_NAME);
        Mockito.when(childDataSchemaNodeRevision.getQName()).thenReturn(QNAME_CHILD_REVISION);
        Mockito.when(childDataSchemaNodeNamespace.getQName()).thenReturn(QNAME_CHILD_NAMESPACE);
        Mockito.when(childDataSchemaNodeFeature.getQName()).thenReturn(QNAME_CHILD_FEATURE);
        final Collection<DataSchemaNode> childNodes = listChilds;
        Mockito.when(dataSchemaNode.getChildNodes()).thenReturn(childNodes);

        final ContainerSchemaNode contDataSchemaNode = Mockito.mock(ContainerSchemaNode.class);
        Mockito.when(this.schemaController.getRestconfModuleRestConfSchemaNode(restconfModule,
                Draft09.RestConfModule.MODULES_CONTAINER_SCHEMA_NODE)).thenReturn(contDataSchemaNode);
        Mockito.when(contDataSchemaNode.getQName()).thenReturn(QNAME_CONT);
        Mockito.when(contDataSchemaNode.getChildNodes()).thenReturn(childNodes);
    }

    @Test
    public void getModuleTest() throws Exception {
        final Module module = Mockito.mock(Module.class);
        Mockito.when(this.schemaController.findModuleByNameAndRevision(this.MODUL_QNAME)).thenReturn(module);
        Mockito.when(module.getRevision()).thenReturn(MODUL.getRevision());
        Mockito.when(module.getNamespace()).thenReturn(new URI("module"));
        Mockito.when(module.getName()).thenReturn("module");

        final NormalizedNodeContext nnCx = this.service.getModule(MODUL_IDENTIFIER, null);
        Assert.assertNotNull(nnCx);
        Assert.assertEquals("2016-03-09", nnCx.getData().getNodeType().getModule().getFormattedRevision());
        Assert.assertEquals("name", nnCx.getData().getNodeType().getNamespace().toString());
        Assert.assertEquals("name", nnCx.getData().getNodeType().getLocalName());
    }

    @Test
    public void getModules() throws Exception {
        final Module module = Mockito.mock(Module.class);
        Mockito.when(module.getRevision()).thenReturn(MODUL.getRevision());
        Mockito.when(module.getNamespace()).thenReturn(new URI("module"));
        Mockito.when(module.getName()).thenReturn("module");

        final Module module2 = Mockito.mock(Module.class);
        Mockito.when(module2.getRevision()).thenReturn(MODUL2.getRevision());
        Mockito.when(module2.getNamespace()).thenReturn(new URI("module"));
        Mockito.when(module2.getName()).thenReturn("module");

        final Set<Module> allModules = new HashSet<>();
        allModules.add(module);
        allModules.add(module2);
        Mockito.when(this.schemaController.getAllModules()).thenReturn(allModules);

        final NormalizedNodeContext modules = this.service.getModules(null);
        Assert.assertNotNull(modules);
    }
}
