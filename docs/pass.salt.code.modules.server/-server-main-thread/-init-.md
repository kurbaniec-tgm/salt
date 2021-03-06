[salt](../../index.md) / [pass.salt.code.modules.server](../index.md) / [ServerMainThread](index.md) / [&lt;init&gt;](./-init-.md)

# &lt;init&gt;

`ServerMainThread(executor: `[`SaltThreadPool`](../../pass.salt.code.modules/-salt-thread-pool/index.md)`, serverSocket: P, mapping: `[`MutableMap`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-mutable-map/index.html)`<`[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, `[`Mapping`](../../pass.salt.code.modules.server.mapping/-mapping/index.md)`>, config: `[`Config`](../../pass.salt.code.loader.config/-config/index.md)`, security: `[`Pair`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-pair/index.html)`<`[`Boolean`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html)`, `[`SaltSecurity`](../../pass.salt.code.modules.server.security/-salt-security/index.md)`?>)`

Main server thread that listens for http- or https client connections and spawns a [ServerWorkerThread](../-server-worker-thread/index.md)
for every new connection request.

