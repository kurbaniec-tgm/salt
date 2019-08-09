package pass.salt.modules.server

import pass.salt.container.Container
import pass.salt.loader.config.Config
import pass.salt.modules.SaltProcessor
import pass.salt.modules.SaltThreadPool
import pass.salt.modules.server.encryption.SSLManager
import java.io.FileInputStream
import java.net.ServerSocket
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.SSLServerSocketFactory
import java.io.FileOutputStream
import sun.security.x509.X500Name
import sun.security.tools.keytool.CertAndKeyGen
import java.security.cert.X509Certificate
import kotlin.reflect.KFunction


class PepperServer(
        val config: Config,
        val container: Container
): SaltProcessor {
    val mapping = mutableMapOf<String, MutableMap<String, Pair<Any, KFunction<*>>>>()
    lateinit var serverHttp: ServerMainThread<ServerSocket>
    lateinit var serverHttps: ServerMainThread<SSLServerSocket>

    init {
        val getMapping = mutableMapOf<String, Pair<Any, KFunction<*>>>()
        val postMapping = mutableMapOf<String, Pair<Any, KFunction<*>>>()
        mapping["get"] = getMapping
        mapping["post"] = postMapping
    }
    override fun process(className: String) {
        container.addElement("pepperServer", this)
        val executor = container.getElement("saltThreadPool") as SaltThreadPool
        if (config.findObjectAttribute("server", "http") as Boolean) {
            val port = config.findObjectAttribute("server", "http_port") as Int
            val socket = ServerSocket(port)
            serverHttp = ServerMainThread(executor, socket, mapping, config)
            container.addElement("serverMainThreadHttp", serverHttp)
            executor.submit(serverHttp)
        }
        if (config.findObjectAttribute("server", "https") as Boolean) {
            val port = config.findObjectAttribute("server", "https_port") as Int
            val password = config.findObjectAttribute("keystore", "password") as String
            SSLManager.createKeyStore(password)
            val sslContext = SSLManager.createSSLContext(password)
            val sslServerSocketFactory = sslContext?.serverSocketFactory
            val socket = sslServerSocketFactory?.createServerSocket(port) as SSLServerSocket
            serverHttps = ServerMainThread(executor, socket, mapping, config)
            container.addElement("serverMainThreadHttps", serverHttps)
            executor.submit(serverHttps)
        }
    }

    fun addGetMapping(path: String, call: Pair<Any, KFunction<*>>) {
        mapping["get"]?.set(path, call)
    }

    fun addPostMapping(path: String, call: Pair<Any, KFunction<*>>) {
        mapping["post"]?.set(path, call)
    }

    override fun shutdown() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


}