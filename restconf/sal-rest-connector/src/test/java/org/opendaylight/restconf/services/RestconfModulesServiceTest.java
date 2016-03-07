/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.services;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
    private static final QName LIST_QNAME = QName.create("list:l", "2016-03-09", "l");

    private static final QName QNAME_CHILD_NAME = QName.create("list:l", "2016-03-09", "name");
    private static final QName QNAME_CHILD_REVISION = QName.create("list:l", "2016-03-09", "revision");
    private static final QName QNAME_CHILD_NAMESPACE = QName.create("list:l", "2016-03-09", "namespace");
    private static final QName QNAME_CHILD_FEATURE = QName.create("list:l", "2016-03-09", "feature");

    private RestconfModulesService service;

    @Mock
    RestSchemaController schemaController;

    @Before
    public void setup() throws Exception {
        this.service = new RestconfModulesServiceImpl(this.schemaController);
        Assert.assertNotNull(this.service);

        this.MODUL_QNAME = QName.create(null, new SimpleDateFormat("yyyy-MM-dd").parse("2016-03-09"), "module");
    }

    @Test
    public void test() throws Exception {
        final Module module = Mockito.mock(Module.class);
        Mockito.when(this.schemaController.findModuleByNameAndRevision(this.MODUL_QNAME)).thenReturn(module);
        Mockito.when(module.getRevision()).thenReturn(LIST_QNAME.getRevision());
        Mockito.when(module.getNamespace()).thenReturn(new URI("module"));
        Mockito.when(module.getName()).thenReturn("module");

        final SchemaContext schemaContext = Mockito.mock(SchemaContext.class);
        Mockito.when(this.schemaController.getGlobalSchema()).thenReturn(schemaContext);

        final Module restconfModule = Mockito.mock(Module.class);
        Mockito.when(this.schemaController.getRestconfModule()).thenReturn(restconfModule);

        final ListSchemaNode dataSchemaNode = Mockito.mock(ListSchemaNode.class);
        Mockito.when(this.schemaController.getRestconfModuleRestConfSchemaNode(restconfModule,
                Draft09.RestConfModule.MODULE_LIST_SCHEMA_NODE)).thenReturn(dataSchemaNode);
        Mockito.when(dataSchemaNode.getQName()).thenReturn(LIST_QNAME);

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
        final NormalizedNodeContext nnCx = this.service.getModule(MODUL_IDENTIFIER, null);
    }
}
