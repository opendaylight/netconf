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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.yangtools.util.xml.UntrustedXML;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class FilterTest {

    private Filter filter;
    @Mock
    private FilterContentValidator validator;

    @Before
    public void setUp() throws Exception {
        XMLUnit.setIgnoreWhitespace(true);
        MockitoAnnotations.initMocks(this);
        filter = new Filter(validator);
    }

    @Test
    public void testGetDataRootsFromFilter() throws Exception {
        final String ns1 = "urn:opendaylight:mdsal:mapping:test";
        final QName top = QName.create(ns1, "top");
        final QName users = QName.create(top, "users");
        final YangInstanceIdentifier path1 = YangInstanceIdentifier.builder()
                .node(top)
                .node(users)
                .build();
        final String ns2 = "urn:opendaylight:mdsal:mapping:test";
        final QName top2 = QName.create(ns2, "top2");
        final QName users2 = QName.create(top2, "users2");
        final YangInstanceIdentifier path2 = YangInstanceIdentifier.builder()
                .node(top2)
                .node(users2)
                .build();
        final String ns3 = "urn:opendaylight:mdsal:mapping:test3";
        final QName top3 = QName.create(ns3, "top3");
        final YangInstanceIdentifier path3 = YangInstanceIdentifier.builder()
                .node(top3)
                .build();
        final XmlElement filter1 = XmlElement.fromDomDocument(fromResource("/filter/multiple-roots/r11.xml"));
        final XmlElement filter2 = XmlElement.fromDomDocument(fromResource("/filter/multiple-roots/r12.xml"));
        final XmlElement filter3 = XmlElement.fromDomDocument(fromResource("/filter/multiple-roots/r13.xml"));
        doReturn(path1).when(validator).validate(xmlArgEq(filter1));
        doReturn(path2).when(validator).validate(xmlArgEq(filter2));
        doReturn(path3).when(validator).validate(xmlArgEq(filter3));
        final InputStream xmlStream = getClass().getResourceAsStream("/filter/multiple-roots/mr1.xml");
        final Document xmlDoc = UntrustedXML.newDocumentBuilder().parse(xmlStream);
        final XmlElement filterXml = XmlElement.fromDomDocument(xmlDoc);
        final Collection<Filter.YidFilter> dataRootsFromFilter = filter.getDataRootsFromFilter(filterXml);
        Assert.assertThat(dataRootsFromFilter,
                hasItems(yidFilter(path1, filter1), yidFilter(path2, filter2), yidFilter(path3, filter3)));
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