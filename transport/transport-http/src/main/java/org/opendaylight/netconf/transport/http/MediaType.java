/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import io.netty.util.AsciiString;
import java.util.List;
import java.util.Map.Entry;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A media type specification, as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc9110#name-media-type"RFC9110, section 8.3.1</a>.
 */
@NonNullByDefault
public final class MediaType {
    private final AsciiString type;
    private final AsciiString subtype;

    MediaType(final AsciiString type, final AsciiString subtype) {
        this.type = requireNonNull(type);
        this.subtype = requireNonNull(subtype);
    }

    public AsciiString type() {
        return type;
    }

    public AsciiString subtype() {
        return subtype;
    }

    public List<Entry<AsciiString, AsciiString>> parameters() {

    }

    //        media-type = type "/" subtype parameters
    //        type       = token
    //        subtype    = token

    //        parameters      = *( OWS ";" OWS [ parameter ] )
    //        parameter       = parameter-name "=" parameter-value
    //        parameter-name  = token
    //        parameter-value = ( token / quoted-string )

    // and therefore rules:

    //        HTAB           =  %x09
    //        SP             =  %x20
    //        DQUOTE         =  %x22
    //        DIGIT          =  %x30-39
    //        ALPHA          =  %x41-5A / %x61-7A

    //        OWS = *( SP / HTAB )

    // Need:
    //        token = 1*tchar
    //        tchar = "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+" / "-" / "." /
    //                "^" / "_" / "`" / "|" / "~" / DIGIT / ALPHA
    //        tchar =  %x21 / %x23-27 / %x2A-2B / %x2D-2E / %x5E-5F /  %x60 / %x7C / %x7E

    //                ->     ! = %x21
    //                       # = %x23
    //                       $ = %x24
    //                       % = %x25
    //                       & = %x26
    //                       ' = %x27
    //                       * = %x2A
    //                       + = %x2B
    //                       - = %x2D
    //                       . = %x2E
    //                       ^ = %x5E
    //                       _ = %x5F
    //                       ` = %x60
    //                       | = %x7C
    //                       ~ = %x7E

    //        quoted-string  = DQUOTE *( qdtext / quoted-pair ) DQUOTE


    //        qdtext         = HTAB / SP / %x21 / %x23-5B / %x5D-7E / obs-text

    //        quoted-pair    = "\" ( HTAB / SP / VCHAR / obs-text )

    //        quoted-pair    = "\" ( %x09 / %x21-7E / %x80-FF )

    //        obs-text       = %x80-FF
    //        VCHAR          =  %x21-7E
}
