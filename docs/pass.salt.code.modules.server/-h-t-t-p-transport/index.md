[salt](../../index.md) / [pass.salt.code.modules.server](../index.md) / [HTTPTransport](./index.md)

# HTTPTransport

`class HTTPTransport` [(source)](https://github.com/kurbaniec-tgm/salt/tree/master/code/modules/server/HTTPTransport.kt#L8)

Enables easy building of HTTP responses in Salt.

### Types

| Name | Summary |
|---|---|
| [Body](-body/index.md) | `class Body` |
| [Header](-header/index.md) | `class Header` |

### Constructors

| Name | Summary |
|---|---|
| [&lt;init&gt;](-init-.md) | `HTTPTransport(header: Header)`<br>`HTTPTransport(body: Body)`<br>`HTTPTransport()`<br>Enables easy building of HTTP responses in Salt.`HTTPTransport(header: Header, body: Body)` |

### Properties

| Name | Summary |
|---|---|
| [body](body.md) | `val body: Body` |
| [header](header.md) | `val header: Header` |

### Functions

| Name | Summary |
|---|---|
| [do200](do200.md) | Build 200 - OK response.`fun do200(): `[`HTTPTransport`](./index.md) |
| [do403](do403.md) | Build 403 - Forbidden response.`fun do403(): `[`HTTPTransport`](./index.md) |
| [do423](do423.md) | Build 423 - Locked response.`fun do423(): `[`HTTPTransport`](./index.md) |
| [do424](do424.md) | Build 424 - Failed Dependency response.`fun do424(): `[`HTTPTransport`](./index.md) |
| [failedDependency](failed-dependency.md) | Build 424 - Failed Dependency response.`fun failedDependency(): `[`HTTPTransport`](./index.md) |
| [forbidden](forbidden.md) | Build 403 - Forbidden response.`fun forbidden(): `[`HTTPTransport`](./index.md) |
| [locked](locked.md) | Build 423 - Locked response.`fun locked(): `[`HTTPTransport`](./index.md) |
| [ok](ok.md) | Build 200 - Ok response.`fun ok(): `[`HTTPTransport`](./index.md) |
| [transport](transport.md) | Sends builded response.`fun transport(out: `[`PrintWriter`](https://docs.oracle.com/javase/6/docs/api/java/io/PrintWriter.html)`): `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
