IETF Key Store implementation
=============================
This is basic interface to access symmetric and asymmetric keys. We provide a few for other components:
* a placeholder [api component](keystore-api)
* an [empty implementation](keystore-none)
* the [legacy](keystore-legacy) datastore-based implementation
* an interface to [access plaintext](plaintext-api)
* an implementation [plaintext storage](plaintext-localfile) using an encrypted-at-rest file
* [Karaf CLI](plaintext-cli) to examine and manipulate stored plaintexts

