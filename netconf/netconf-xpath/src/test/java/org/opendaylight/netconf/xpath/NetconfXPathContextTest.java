/*
 * Copyright (c) 2020 Lumina Networks, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.xpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;

public class NetconfXPathContextTest {

    private static final NodeIdentifier CONT_INNER_BASE = NodeIdentifier
            .create(QName.create("ns:uri:b", "2020-02-22", "cont-in"));
    private static final NodeIdentifier CONT_BASE = NodeIdentifier
            .create(QName.create("ns:uri:a", "2020-06-22", "cont"));
    private static final YangInstanceIdentifier BASE_PATH = YangInstanceIdentifier.create(CONT_BASE, CONT_INNER_BASE);
    private List<Set<QName>> fields;

    @Before
    public void setup() {
        fields = new ArrayList<>();
    }

    @Test
    public void prepareXPathWithoutFieldsTest() {
        YangInstanceIdentifier.create(CONT_BASE, CONT_INNER_BASE);
        final NetconfXPathContext xpathContext = NetconfXPathContext.createXPathContext(BASE_PATH, fields);
        xpathContext.addNamespace("ns:uri");
        MatcherAssert.assertThat(xpathContext.getXpathWithPrefixes(), Matchers.equalTo(""));
    }

    @Test
    public void prepareXPathWithSingleFieldTest() {
        fields.add(Collections.singleton(QName.create("ns:uri:b", "2020-02-22", "field")));
        final NetconfXPathContext xpathContext = NetconfXPathContext.createXPathContext(BASE_PATH, fields);
        xpathContext.addNamespace("ns:uri");
        MatcherAssert.assertThat(xpathContext.getXpathWithPrefixes(),
                Matchers.equalTo("/nxpcrpc0:cont/nxpcrpc1:cont-in/nxpcrpc1:field"));
    }

    @Test
    public void prepareXPathWithMultiFieldsTest() {
        final Set<QName> path = new LinkedHashSet<>();
        path.add(QName.create("ns:uri:b", "2020-02-22", "cont-field"));

        final Set<QName> complexField = new LinkedHashSet<>();
        complexField.add(QName.create("ns:uri:b", "2020-02-22", "field1"));
        complexField.add(QName.create("ns:uri:b", "2020-02-22", "field2"));

        fields.add(path);
        fields.add(complexField);

        final NetconfXPathContext xpathContext = NetconfXPathContext.createXPathContext(BASE_PATH, fields);

        xpathContext.addNamespace("ns:uri");
        MatcherAssert.assertThat(xpathContext.getXpathWithPrefixes(),
                Matchers.equalTo("/nxpcrpc0:cont/nxpcrpc1:cont-in/nxpcrpc1:cont-field/nxpcrpc1:field1"
                        + " | /nxpcrpc0:cont/nxpcrpc1:cont-in/nxpcrpc1:cont-field/nxpcrpc1:field2"));
    }
}
