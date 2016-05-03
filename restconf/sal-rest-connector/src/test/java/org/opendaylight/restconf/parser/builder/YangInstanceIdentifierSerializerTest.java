/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.parser.builder;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.restconf.parser.IdentifierCodec;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class YangInstanceIdentifierSerializerTest {

    private SchemaContext schemaContext;
    private final String data = "/list-test:top/list1=%2C%27" + '"' + "%3A" + '"' + "%20%2F,,foo/list2=a,b/result=x";

    @Before
    public void init() throws Exception {
        this.schemaContext = TestRestconfUtils.loadSchemaContext("/restconf/parser");
    }

    @Test
    public void parserTest() {
        final YangInstanceIdentifier dataYangII = YangInstanceIdentifier
                .create(YangInstanceIdentifierDeserializer.create(this.schemaContext, this.data));

        final String serializedDataYangII = IdentifierCodec.serialize(dataYangII, this.schemaContext);
        Assert.assertEquals(this.data, serializedDataYangII);
    }
}
