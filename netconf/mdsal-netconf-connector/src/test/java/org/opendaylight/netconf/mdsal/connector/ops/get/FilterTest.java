/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops.get;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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

    private Filter filter;
    private FilterContentValidator validator;

    @Before
    public void setUp() throws Exception {
        XMLUnit.setIgnoreWhitespace(true);
        final List<InputStream> sources = new ArrayList<>();
        sources.add(getClass().getResourceAsStream("/yang/mdsal-netconf-mapping-test.yang"));
        final SchemaContext context = YangParserTestUtils.parseYangStreams(sources);
        final CurrentSchemaContext currentContext = mock(CurrentSchemaContext.class);
        doReturn(context).when(currentContext).getCurrentContext();
        validator = new FilterContentValidator(currentContext);
        filter = new Filter(validator);
    }

    @Test
    public void testGetDataRootsFromFilter() throws Exception {
        final String ns1 = "urn:opendaylight:mdsal:mapping:test";
        final QName top = QName.create(ns1, "2015-02-26", "top");
        final QName users = QName.create(top, "users");
        final QName user = QName.create(top, "user");
        final YangInstanceIdentifier path1 = YangInstanceIdentifier.builder()
                .node(top)
                .node(users)
                .node(user)
                .build();
        final String ns2 = "urn:opendaylight:mdsal:mapping:test";
        final QName mappingNodes = QName.create(ns2, "2015-02-26", "mapping-nodes");
        final QName multipleKeys = QName.create(mappingNodes, "multiple-keys");
        final QName key1 = QName.create(mappingNodes, "key1");
        final QName key2 = QName.create(mappingNodes, "key2");
        final QName key3 = QName.create(mappingNodes, "key3");
        final Map<QName, Object> keyMap = ImmutableMap.of(key1, "aaa", key2, "bbb", key3, "ccc");
        final YangInstanceIdentifier path2 = YangInstanceIdentifier.builder()
                .node(mappingNodes)
                .node(multipleKeys)
                .nodeWithKey(multipleKeys, keyMap)
                .build();
        final XmlElement filter1 = XmlElement.fromDomDocument(fromResource("/filter/multiple-roots/r11.xml"));
        final XmlElement filter2 = XmlElement.fromDomDocument(fromResource("/filter/multiple-roots/r12.xml"));
        final InputStream xmlStream = getClass().getResourceAsStream("/filter/multiple-roots/mr1.xml");
        final Document xmlDoc = UntrustedXML.newDocumentBuilder().parse(xmlStream);
        final XmlElement filterXml = XmlElement.fromDomDocument(xmlDoc);
        final Collection<Filter.YidFilter> dataRootsFromFilter = filter.getDataRootsFromFilter(filterXml);
        Assert.assertThat(dataRootsFromFilter,
                hasItems(yidFilter(path1, filter1), yidFilter(path2, filter2)));
    }

    private static Document fromResource(final String url) throws IOException, SAXException {
        return UntrustedXML.newDocumentBuilder().parse(FilterTest.class.getResourceAsStream(url));
    }

    private static BaseMatcher<Filter.YidFilter> yidFilter(final YangInstanceIdentifier path, final XmlElement filter) {
        return new BaseMatcher<Filter.YidFilter>() {
            @Override
            public boolean matches(final Object item) {
                if (item instanceof Filter.YidFilter) {
                    final Filter.YidFilter actual = (Filter.YidFilter) item;
                    return path.equals(actual.getPath())
                            && xmlEq(filter).matches(actual.getFilter());
                }
                return false;
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("Path:" + path + " Filter: " + XmlUtil.toString(filter));
            }
        };
    }

    private static XmlElement xmlArgEq(final XmlElement element) {
        final BaseMatcher<XmlElement> matcher = xmlEq(element);
        return argThat(matcher);
    }

    private static BaseMatcher<XmlElement> xmlEq(final XmlElement element) {
        return new BaseMatcher<XmlElement>() {
            @Override
            public void describeTo(final Description description) {
                description.appendText(XmlUtil.toString(element));
            }

            @Override
            public boolean matches(final Object argument) {
                if (argument instanceof XmlElement) {
                    final XmlElement xmlArg = (XmlElement) argument;
                    final Document filterDoc = UntrustedXML.newDocumentBuilder().newDocument();
                    final Node node = filterDoc.importNode(xmlArg.getDomElement(), true);
                    filterDoc.appendChild(node);
                    final Document expected = element.getDomElement().getOwnerDocument();
                    final Diff diff = XMLUnit.compareXML(expected, filterDoc);
                    return diff.similar();
                }
                return false;
            }
        };
    }

}