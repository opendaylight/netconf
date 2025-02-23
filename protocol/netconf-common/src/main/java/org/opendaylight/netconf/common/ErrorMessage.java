/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The contents of a {@code error-message} element as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#page-83">RFC8040 errors grouping</a>. This object can optionally
 * transport the <a href="https://www.w3.org/TR/xml/#sec-lang-tag">Language Identification</a> as conveyed via,
 * for example, <a href="https://www.rfc-editor.org/rfc/rfc6241#page-17">RFC6241 error-message element</a>.
 *
 * @param elementBody the string to be displayed
 * @param xmlLang optional Language Identification string
 */
@NonNullByDefault
// TODO: consider sharing this class with netconf-api's NetconfDocumentedException
public record ErrorMessage(String elementBody, @Nullable String xmlLang) {
    public ErrorMessage {
        requireNonNull(elementBody);
    }

    public ErrorMessage(final String elementBody) {
        this(elementBody, null);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).omitNullValues()
            .add("elementBody", elementBody)
            .add("xmlLang", xmlLang)
            .toString();
    }
}