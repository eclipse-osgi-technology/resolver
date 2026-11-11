# OSGi Resolver

A vendor neutral home for OSGi resolvers, resolver tooling and resolver conformance tests.

## Goals

This project is part of [Eclipse OSGi Technology](https://github.com/eclipse-osgi-technology) and has three main goals:

1. **Implement the specification**:
   Provide an implementation of the [OSGi Resolver Service Specification](https://docs.osgi.org/specification/osgi.core/8.0.0/service.resolver.html) (`org.osgi.service.resolver`) on vendor neutral ground.
   We also want to give input to and help drive forward the evolution of the specification itself, see [osgi/org.osgi.service.resolver](https://github.com/osgi/org.osgi.service.resolver).
2. **Provide tools for building resolvers**:
   Offer reusable building blocks (e.g. requirement/capability models, wiring helpers, candidate selection and diagnostics) so that framework and tool authors can build their own resolvers without starting from scratch.
3. **Provide a comprehensive test suite**:
   Maintain a test suite that exercises the resolver specification and its edge cases against any implementation.
   This allows testing and comparing different implementations with each other, and finding behavioral differences early.

## Other implementations

This project does not exist in a vacuum, there are already other open source implementations we respect and learn from:

- [Apache Felix Resolver](https://github.com/apache/felix-dev/) (`org.apache.felix.resolver`), the resolver used by many OSGi frameworks and tools.

Our test suite is intended to be usable against these implementations as well, so that the whole ecosystem can benefit.

## Repository status

The repository is in its initial setup phase and grows module by module.

| Module                           | Description                                                                                 |
| -------------------------------- | ------------------------------------------------------------------------------------------- |
| [index](index)                   | Resolver independent lookup structures over the resources and capabilities of a resolve     |
| [verifier](verifier)             | Resolver independent checks that a resolve result is well formed and class space consistent |
| [test-suite](test-suite)         | Cucumber scenarios that exercise a resolver through the OSGi Resolver Service API           |
| [felix-resolver](felix-resolver) | Runs the test suite against the Apache Felix resolver                                       |

Build with JDK 25 and `mvn verify`.

## Contributing

Contributions are welcome.
Please open an issue to discuss larger changes before submitting a pull request.

## License

This project is licensed under the [Eclipse Public License 2.0](LICENSE) (`EPL-2.0`).
