# NETCONF and RESTCONF protocol implementations

Components in this directory are tasked with providing the Messages and
Operations layers for both RFC6241 NETCONF and RFC8040 RESTCONF.

For NETCONF we have
* DOMSource-based [NETCONF Messages](netconf-api) with constants to go as the basic protocol bits
* [codec](netconf-codec) to perform marshalling to/from
  Netty's [ByteBuf](https://javadoc.io/doc/io.netty/netty-buffer/4.1.114.Final/index.html)
* [NETCONF client](netconf-client) library
* [NETCONF server](netconf-server) library
* [NETCONF session](netconf-common) abstraction shared by both

For RESTCONF we have
* java.io-centrict [RESTCONF Messages](restconf-api) with constants and RFC8040 error handling
* [Netty RESTCONF server](restconf-server) HTTP endpoint
* [JAX-RS RESTCONF server](restconf-server-jaxrs) HTTP endpont
* Java [RESTCONF server interface](restconf-server-api) for endpoint's use
* Java [RESTCONF server library](restconf-server-spi) for building servers

Both implementations share
* [test utilities](netconf-test-util) for driving Java tests

There is a notable difference in where we take over from the Netty pipeline:
* for ```netconf-*``` we integrate with raw ByteBufs
* for ```restconf-*``` we integrate with
  [netty-codec-http](https://netty.io/4.2/api/io/netty/handler/codec/http/package-summary.html)
  and [netty-codec-http2](https://netty.io/4.2/api/io/netty/handler/codec/http2/package-summary.html),
  where a lot of the pipelined currently resides in transport/transport-http
  in terms of HttpRequest, HttpHeaders, etc.)
