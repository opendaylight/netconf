package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

public sealed interface StreamEncoding
    permits StreamEncoding.Rfc8040Encoding, StreamEncoding.Rfc8639Encoding {

    /**
     * The local token name ("json", "xml", "encode-json", "encode-xml").
     */
    String localName();

    /**
     * Parse an encoding into the corresponding StreamEncoding.
     *
     * @param encoding the encoding to parse
     * @return the matching StreamEncoding
     * @throws NullPointerException if encoding is null
     * @throws IllegalArgumentException if encoding is not one of the four legal tokens
     */
    static StreamEncoding parse(final String encoding) {
        requireNonNull(encoding, "encoding");
        return switch (encoding) {
            case "json" -> Rfc8040Encoding.JSON;
            case "xml" -> Rfc8040Encoding.XML;
            case "encode-json" -> Rfc8639Encoding.ENCODE_JSON;
            case "encode-xml" -> Rfc8639Encoding.ENCODE_XML;
            default -> throw new IllegalArgumentException("Unknown encoding: " + encoding);
        };
    }

/**
 * RFC 8040 style encodings.
 */
enum Rfc8040Encoding implements StreamEncoding {
    JSON("json"),
    XML("xml");

    private final String localName;

    Rfc8040Encoding(final String localName) {
        this.localName = localName;
    }

    @Override
    public String localName() {
        return localName;
    }
}

/**
 * RFC 8639 style encodings.
 */
enum Rfc8639Encoding implements StreamEncoding {
    ENCODE_JSON("encode-json"),
    ENCODE_XML("encode-xml");

    private final String localName;

    Rfc8639Encoding(final String localName) {
        this.localName = localName;
    }

    @Override
    public String localName() {
        return localName;
    }
}
}
