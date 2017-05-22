/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops.get;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class FilterTest {

    public static final String URN_OPENDAYLIGHT_MDSAL_MAPPING_TEST = "urn:opendaylight:mdsal:mapping:test";
    private Filter filter;
    private FilterContentValidator validator;

    @Before
    public void setUp() throws Exception {
        XMLUnit.setIgnoreWhitespace(true);
        List<InputStream> sources = Lists.newArrayList(getClass()
                .getResourceAsStream("/yang/mdsal-netconf-mapping-test.yang"));
        SchemaContext context = YangParserTestUtils.parseYangStreams(sources);
        CurrentSchemaContext currentContext = mock(CurrentSchemaContext.class);
        doReturn(context).when(currentContext).getCurrentContext();
        validator = new FilterContentValidator(currentContext);
        filter = new Filter(validator);
    }

    @Test
    public void testGetDataRootsFromFilter() throws Exception {
        YangInstanceIdentifier path1 = createTopPath();
        YangInstanceIdentifier path2 = createMultipleKeysPath();

        XmlElement filter1 = XmlElement.fromDomDocument(fromResource("/filter/multiple-roots/get-element-1.xml"));
        XmlElement filter2 = XmlElement.fromDomDocument(fromResource("/filter/multiple-roots/get-element-2.xml"));
        XmlElement filterXml = XmlElement.fromDomDocument(
                fromResource("/filter/multiple-roots/response-merged-elements.xml"));

        Collection<Filter.YidFilter> dataRootsFromFilter = filter.getDataRootsFromFilter(filterXml);
        Assert.assertThat(dataRootsFromFilter,
                hasItems(yidFilter(path1, filter1), yidFilter(path2, filter2)));
    }

    private YangInstanceIdentifier createMultipleKeysPath() {
        QName mappingNodes = QName.create(URN_OPENDAYLIGHT_MDSAL_MAPPING_TEST, "2015-02-26", "mapping-nodes");
        QName multipleKeys = QName.create(mappingNodes, "multiple-keys");
        QName key1 = QName.create(mappingNodes, "key1");
        QName key2 = QName.create(mappingNodes, "key2");
        QName key3 = QName.create(mappingNodes, "key3");
        Map<QName, Object> keyMap = ImmutableMap.of(key1, "aaa", key2, "bbb", key3, "ccc");
        return YangInstanceIdentifier.builder()
                .node(mappingNodes)
                .node(multipleKeys)
                .nodeWithKey(multipleKeys, keyMap)
                .build();
    }

    private YangInstanceIdentifier createTopPath() {
        QName top = QName.create(URN_OPENDAYLIGHT_MDSAL_MAPPING_TEST, "2015-02-26", "top");
        QName users = QName.create(top, "users");
        QName user = QName.create(top, "user");
        QName name = QName.create(top, "name");
        Map<QName, Object> keyMap = ImmutableMap.of(name, "root");
        return YangInstanceIdentifier.builder()
                .node(top)
                .node(users)
                .node(user)
                .nodeWithKey(user, keyMap)
                .build();
    }

    private Document fromResource(String url) throws IOException, SAXException {
        return UntrustedXML.newDocumentBuilder().parse(FilterTest.class.getResourceAsStream(url));
    }

    private BaseMatcher<Filter.YidFilter> yidFilter(YangInstanceIdentifier path, XmlElement filter) {
        return new BaseMatcher<Filter.YidFilter>() {
            @Override
            public boolean matches(Object item) {
                if (item instanceof Filter.YidFilter) {
                    Filter.YidFilter actual = (Filter.YidFilter) item;
                    return path.equals(actual.getPath())
                            && xmlEq(filter).matches(actual.getFilter());
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Path:" + path + " Filter: " + XmlUtil.toString(filter));
            }
        };
    }


    private BaseMatcher<XmlElement> xmlEq(XmlElement element) {
        return new BaseMatcher<XmlElement>() {
            @Override
            public void describeTo(Description description) {
                description.appendText(XmlUtil.toString(element));
            }

            @Override
            public boolean matches(Object argument) {
                if (argument instanceof XmlElement) {
                    XmlElement xmlArg = (XmlElement) argument;
                    Document filterDoc = UntrustedXML.newDocumentBuilder().newDocument();
                    Node node = filterDoc.importNode(xmlArg.getDomElement(), true);
                    filterDoc.appendChild(node);
                    Document expected = element.getDomElement().getOwnerDocument();
                    Diff diff = XMLUnit.compareXML(expected, filterDoc);
                    return diff.similar();
                }
                return false;
            }
        };
    }
}