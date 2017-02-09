/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops.get;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.model.InitializationError;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

@RunWith(value = Parameterized.class)
public class FilterContentValidatorTest {

    private static final int TEST_CASE_COUNT = 12;
    private static final Pattern LIST_ENTRY_PATTERN = Pattern.compile("(.*)\\[\\{(.*)(, .*)+\\}\\]");
    private final XmlElement filterContent;
    private final String expected;
    private FilterContentValidator validator;


    @Parameterized.Parameters
    public static Collection<Object[]> data() throws IOException, SAXException, URISyntaxException, InitializationError {
        final List<Object[]> result = new ArrayList<>();
        final Path path = Paths.get(FilterContentValidatorTest.class.getResource("/filter/expected.txt").toURI());
        final List<String> expected = Files.readAllLines(path);
        if (expected.size() != TEST_CASE_COUNT) {
            throw new InitializationError("Number of lines in results file must be same as test case count");
        }
        for (int i = 1; i <= TEST_CASE_COUNT; i++) {
            final Document document = XmlUtil.readXmlToDocument(FilterContentValidatorTest.class.getResourceAsStream("/filter/f" + i + ".xml"));
            result.add(new Object[]{document, expected.get(i-1)});
        }
        return result;
    }

    public FilterContentValidatorTest(final Document filterContent, final String expected) {
        this.filterContent = XmlElement.fromDomDocument(filterContent);
        this.expected = expected;
    }

    @Before
    public void setUp() throws Exception {
        final List<InputStream> sources = new ArrayList<>();
        sources.add(getClass().getResourceAsStream("/yang/filter-validator-test-mod-0.yang"));
        sources.add(getClass().getResourceAsStream("/yang/filter-validator-test-augment.yang"));
        final SchemaContext context = YangParserTestUtils.parseYangStreams(sources);
        final CurrentSchemaContext currentContext = mock(CurrentSchemaContext.class);
        doReturn(context).when(currentContext).getCurrentContext();
        validator = new FilterContentValidator(currentContext);
    }

    @Test
    public void testValidate() throws Exception {
        if (expected.startsWith("success")) {
            final String expId = expected.replace("success=", "");
//            Assert.assertEquals(expId, validator.validate(filterContent).toString());
            yidStringEquals(expId, validator.validate(filterContent).toString());
        } else if (expected.startsWith("error")) {
            try {
                validator.validate(filterContent);
                Assert.fail(XmlUtil.toString(filterContent) + " is not valid and should throw exception.");
            } catch (final Exception e) {
                final String expectedExceptionClass = expected.replace("error=", "");
                Assert.assertEquals(expectedExceptionClass, e.getClass().getName());
            }
        }

    }

    private static void yidStringEquals(final String expected, final String actual) {
        final Iterator<String> expIt = Arrays.asList(expected.split("/")).listIterator();
        final Iterator<String> acIt = Arrays.asList(actual.split("/")).listIterator();
        while (expIt.hasNext() && acIt.hasNext()) {
            final String expElement = expIt.next();
            final String acElement = acIt.next();
            final Matcher expMatcher = LIST_ENTRY_PATTERN.matcher(expElement);
            final Matcher acMatcher = LIST_ENTRY_PATTERN.matcher(acElement);
            if (expMatcher.matches() && acMatcher.matches()) {
                Assert.assertEquals(expMatcher.groupCount(), acMatcher.groupCount());
                Assert.assertEquals(expMatcher.group(1), acMatcher.group(1));
                final Set<String> expKeys = new HashSet<>();
                final Set<String> acKeys = new HashSet<>();
                for (int i = 2; i < expMatcher.groupCount(); i++) {
                    expKeys.add(expMatcher.group(i));
                    acKeys.add(acMatcher.group(i));
                }
                Assert.assertEquals(expKeys, acKeys);
            } else {
                Assert.assertEquals(expElement, acElement);
            }
        }
    }
}
