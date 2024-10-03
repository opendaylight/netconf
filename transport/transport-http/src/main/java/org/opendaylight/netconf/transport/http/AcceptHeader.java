/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A parsed value of <a href ="https://www.rfc-editor.org/rfc/rfc9110#name-accept">Accept</a> header.
 */
@NonNullByDefault
final class AcceptHeader {

    private AcceptHeader() {
        // Hidden on purpose
    }

    static AcceptHeader parse(final List<String> allValues) {
        // FIXME: use ANTLR for this

        //        Accept = #( media-range [ weight ] )
        //
        //        media-range    = ( "*/*"
        //                           / ( type "/" "*" )
        //                           / ( type "/" subtype )
        //                         ) parameters
        //        weight = OWS ";" OWS "q=" qvalue
        //
        //        OWS = *( SP / HTAB )
        //        parameters = *( OWS ";" OWS [ parameter ] )
        //        qvalue = ( "0" [ "." *3DIGIT ] ) / ( "1" [ "." *3"0" ] )
        //        subtype = token
        //        type = token
        //
        //        parameter = parameter-name "=" parameter-value
        //        parameter-name = token
        //        parameter-value = ( token / quoted-string )
        //        token = 1*tchar
        //        tchar = "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+" / "-" / "." /
        //                "^" / "_" / "`" / "|" / "~" / DIGIT / ALPHA
        //        quoted-string  = DQUOTE *( qdtext / quoted-pair ) DQUOTE
        //        qdtext         = HTAB / SP / %x21 / %x23-5B / %x5D-7E / obs-text
        //        quoted-pair    = "\" ( HTAB / SP / VCHAR / obs-text )
        //        obs-text       = %x80-FF
        //        VCHAR          =  %x21-7E
        //        DQUOTE         =  %x22
        //        HTAB           =  %x09
        //        SP             =  %x20
        //        DIGIT          =  %x30-39
        //        ALPHA          =  %x41-5A / %x61-7A

        throw new UnsupportedOperationException();
    }
}
