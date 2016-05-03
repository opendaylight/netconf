/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.parser.builder;

import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.sal.restconf.impl.test.TestUtils;
import org.opendaylight.restconf.parser.IdentifierCodec;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

@RunWith(MockitoJUnitRunner.class)
public class YangInstanceIdentifierSerializerBuilderTest {

    private SchemaContext schemaContext;
    private final String data = "/list-test:top/list1=%2C%27" + '"' + "%3A" + '"' + "%20%2F,,foo/list2=a,b/result=x";

    @Before
    public void init() {
        final Set<Module> modules = TestUtils.loadModulesFrom("/restconf/parser");
        Assert.assertNotNull(modules);
        this.schemaContext = TestUtils.loadSchemaContext(modules);
    }

    @Test
    public void test() {
        final YangInstanceIdentifierDeserializerBuilder builder = new YangInstanceIdentifierDeserializerBuilder(
                this.schemaContext, this.data);
        final YangInstanceIdentifier create = YangInstanceIdentifier.create(builder.build());

        final IdentifierCodec codec = new IdentifierCodec(this.schemaContext);
        final String serialize = codec.serialize(create);
        Assert.assertEquals(this.data, serialize);
    }
}
