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

import com.google.common.base.Preconditions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.w3c.dom.Document;

@RunWith(value = Parameterized.class)
public class FilterContentValidatorTest {

    private static final int TEST_CASE_COUNT = 13;
    private static final Pattern LIST_ENTRY_PATTERN =
            Pattern.compile("(?<listName>.*)\\[\\{(?<keys>(.*)(, .*)*)\\}\\]");
    private static final Pattern KEY_VALUE_PATTERN =
            Pattern.compile("(?<key>\\(.*\\).*)=(?<value>.*)");
    private final XmlElement filterContent;
    private final String expected;
    private FilterContentValidator validator;

    @Parameterized.Parameters
    public static Collection<Object[]> data() throws Exception {
        final List<Object[]> result = new ArrayList<>();
        final Path path = Paths.get(FilterContentValidatorTest.class.getResource("/filter/expected.txt").toURI());
        final List<String> expected = Files.readAllLines(path);
        if (expected.size() != TEST_CASE_COUNT) {
            throw new InitializationError("Number of lines in results file must be same as test case count");
        }
        for (int i = 1; i <= TEST_CASE_COUNT; i++) {
            final Document document = XmlUtil.readXmlToDocument(FilterContentValidatorTest.class.getResourceAsStream(
                    "/filter/f" + i + ".xml"));
            result.add(new Object[]{document, expected.get(i - 1)});
        }
        return result;
    }

    public FilterContentValidatorTest(final Document filterContent, final String expected) {
        this.filterContent = XmlElement.fromDomDocument(filterContent);
        this.expected = expected;
    }

    @Before
    public void setUp() throws Exception {
        final SchemaContext context = YangParserTestUtils.parseYangResources(FilterContentValidatorTest.class,
            "/yang/filter-validator-test-mod-0.yang", "/yang/filter-validator-test-augment.yang",
            "/yang/mdsal-netconf-mapping-test.yang");

        final CurrentSchemaContext currentContext = mock(CurrentSchemaContext.class);
        doReturn(context).when(currentContext).getCurrentContext();
        validator = new FilterContentValidator(currentContext);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @Test
    public void testValidate() throws Exception {
        if (expected.startsWith("success")) {
            final String expId = expected.replace("success=", "");
            final YangInstanceIdentifier actual = validator.validate(filterContent);
            final YangInstanceIdentifier expected = fromString(expId);
            Assert.assertEquals(expected, actual);
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

    private static YangInstanceIdentifier fromString(final String input) {
        //remove first /
        final String yid = input.substring(1);
        final List<String> pathElements = Arrays.asList(yid.split("/"));
        final YangInstanceIdentifier.InstanceIdentifierBuilder builder = YangInstanceIdentifier.builder();
        //if not specified, PathArguments inherit namespace and revision from previous PathArgument
        QName prev = null;
        for (final String pathElement : pathElements) {
            final Matcher matcher = LIST_ENTRY_PATTERN.matcher(pathElement);
            if (matcher.matches()) {
                prev = parseListEntry(builder, prev, matcher);
            } else {
                final QName qName = createNodeQName(prev, pathElement);
                builder.node(qName);
                prev = qName;
            }
        }
        return builder.build();
    }

    private static QName parseListEntry(final YangInstanceIdentifier.InstanceIdentifierBuilder builder,
                                        final QName prev, final Matcher matcher) {
        final Map<QName, Object> keys = new HashMap<>();
        final String listName = matcher.group("listName");
        final QName listQName = createNodeQName(prev, listName);
        final String keysString = matcher.group("keys");
        final String[] split = keysString.split(",");
        for (final String s : split) {
            final Matcher keyMatcher = KEY_VALUE_PATTERN.matcher(s.trim());
            if (keyMatcher.matches()) {
                final QName keyName = QName.create(keyMatcher.group("key"));
                final String keyValue = keyMatcher.group("value");
                keys.put(keyName, keyValue);
            }
        }
        builder.nodeWithKey(listQName, keys);
        return prev;
    }

    private static QName createNodeQName(final QName prev, final String input) {
        try {
            return QName.create(input);
        } catch (IllegalArgumentException e) {
            return QName.create(Preconditions.checkNotNull(prev), input);
        }
    }
}
