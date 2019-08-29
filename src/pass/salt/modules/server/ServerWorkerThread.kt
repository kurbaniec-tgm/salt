package pass.salt.modules.server

import pass.salt.exceptions.ExceptionsTools
import java.io.*
import java.net.Socket
import java.util.*
import pass.salt.loader.config.Config
import pass.salt.modules.server.security.SaltSecurity
import pass.salt.modules.server.security.SessionUser
import pass.salt.modules.server.webparse.Model
import pass.salt.modules.server.webparse.WebTools
import pass.salt.modules.server.webparse.Webparse
import java.io.BufferedOutputStream
import java.io.PrintWriter
import java.io.InputStreamReader
import java.io.BufferedReader
import java.lang.Exception
import java.net.ServerSocket
import javax.net.ssl.SSLSocket
import java.lang.StringBuilder
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Files
import java.util.logging.Logger
import java.util.stream.Collectors
import javax.activation.MimetypesFileTypeMap


class ServerWorkerThread<P: ServerSocket, S: Socket>(
        val socket: S,
        val server: ServerMainThread<P>,
        val config: Config,
        security: Pair<Boolean, SaltSecurity?>
): Runnable {
    val inp = BufferedReader(InputStreamReader(socket.getInputStream()))
    val out = PrintWriter(socket.getOutputStream())
    val data = BufferedOutputStream(socket.getOutputStream())
    val WEB_ROOT: File
    val METHOD_NOT_SUPPORTED = "not_supported.html"
    val FILE_NOT_FOUND = "404.html"
    var listening = true
    var secOn = false
    var sec: SaltSecurity? = null
    val log = Logger.getGlobal()

    init {
        val path = System.getProperty("user.dir")
        WEB_ROOT = File(path, "res\\web")
        if (security.first) {
            secOn = security.first
            sec = security.second
        }
    }

    data class Request(val method: String, val path: String, val file: String, val params: MutableMap<String, String>, val ipaddress: String)

    override fun run() {
        if (socket is SSLSocket) {
            socket.enabledCipherSuites = socket.supportedCipherSuites;
            socket.startHandshake()
        } else if (config.findObjectAttribute("server", "redirect") as Boolean) {
            val request = readRequest()
            redirect(request.path)
            return
        }
        // TODO kill handler after a time - Terminate Service?
        while (listening) {
            val request = readRequest()
            val header = readHeader()
            readBody(request, header)
            var sid = "NA"
            // Unsupported mapping
            if (request.method != "GET" && request.method != "POST" && request.method != "HEAD") {
                val file = File(WEB_ROOT, METHOD_NOT_SUPPORTED)
                val contentMimeType = "text/html"
                val fileData = readFileData(file, null)
                sendHelper("HTTP/1.1 501 Not Implemented",
                        "Server: SaltApplication",
                        contentMimeType, fileData.second, fileData.first)
            } else {
                if (secOn) {    // Is SaltSecurity activated
                    var secured = false
                    if (sec!!.open) { // mapped entries are only secured
                        if (sec!!.mapping.contains(request.path) || sec!!.mapping.contains("/"+request.file)) {
                            secured = true
                        }
                    } else { // all entries beside mapped ones are secured
                        if (!sec!!.mapping.contains(request.path) && !sec!!.mapping.contains(request.file)) {
                            secured = true
                        }
                    }
                    if (secured) {
                        // containsKey("Cookie") -> check if id is valid
                        // if not -> authorization or redirect
                        var authFailed = true
                        if (header.containsKey("Cookie")) {
                            val cookieRaw = header["Cookie"]
                            if (cookieRaw != null && cookieRaw.contains("_sid")) {
                                sid = getSID(cookieRaw)
                                if (sec!!.isValidSession(sid)) {
                                    if (request.path == sec!!.logout) {
                                        stopSession(sid)
                                        continue
                                    } else if (request.path != sec!!.login) {
                                        authFailed = false
                                    }
                                }
                            }
                        }
                        if (authFailed) {
                            if (header.containsKey("Authorization")) {
                                authenticate(header)
                                continue
                            } else if (request.path != sec!!.login) {
                                login()
                                continue
                            }
                        }
                    }
                }
                val mapping = when (request.method) {
                    "GET" -> server.getGetMapping(request.path)
                    "POST" -> server.getPostMapping(request.path)
                    else -> null
                }
                // Don´t allow serving html files without Controller mapping
                if (mapping == null && (request.file.endsWith(".html") || request.file == "")) {
                    fileNotFound()
                }
                // Look for resources
                else if (mapping == null) {
                    //val search = request.path+request.file
                    //val search = request.file
                    //val searchOr = request.file.replace("/", "\\")
                    val fullSearch = request.file.replace("/", "\\")
                    var directory = "\\"
                    var search = fullSearch
                    if (fullSearch.contains("\\")) {
                        directory = fullSearch.substring(0, fullSearch.lastIndexOf("\\")+1)
                        search = fullSearch.substring(directory.length)
                    }
                    val type = "." + request.file.split(".")[1]
                    var file: File? = null
                    var found = false
                    // TODO Optimze file search?
                    File(WEB_ROOT, directory).listFiles().forEach {
                        if (!found) {
                            if (it.absolutePath.contains(search) && it.absolutePath.endsWith(type)) {
                                file = it
                                found = true
                            }
                        }
                    }
                    if (file != null) {
                        val contentType = getContentType(file!!)
                        val fileData = readFileData(file!!, null)
                        sendHelper("HTTP/1.1 200 OK",
                                "Server: SaltApplication",
                                contentType, fileData.second, fileData.first)
                    }
                    else fileNotFound()
                // Normal mapping via controller
                } else {
                    //mapping.model = Model() //
                    if (mapping.model != null) {
                        mapping.model!!.clear()
                        mapping.model!!.addAttribute("ipaddress", request.ipaddress)
                    }
                    mapping.addParams(request.params)
                    val model = mapping.model

                    if (mapping.hasSessionUser) {
                        val sessionUser = sec!!.sessions[sid]
                        if (sessionUser != null) {
                            mapping.params[mapping.sessionUserKey] = sessionUser
                        }
                        else mapping.params[mapping.sessionUserKey] = SessionUser()
                    }
                    val fileRaw = mapping.call()
                    if (fileRaw is String) {
                        val file = File(WEB_ROOT, fileRaw)
                        val fileLength = file.length().toInt()
                        val content = getContentType(file)
                        val fileData = readFileData(file, model)
                        sendHelper("HTTP/1.1 200 OK",
                                "Server: SaltApplication",
                                content, fileData.second, fileData.first)
                    }
                    else if (fileRaw is HTTPTransport) {
                        fileRaw.transport(out)
                    } // TODO if not HTTPTransport?
                }
            }
        }
    }

    private fun sendHelper(header: String, server: String, contentType: String, fileLength: Int, fileData: ByteArray) {
        out.println(header)
        out.println(server)
        out.println("Date: ${Date()}")
        out.println("Content-type: $contentType")
        out.println("Content-length: $fileLength")
        out.println()
        out.flush()
        data.write(fileData, 0, fileLength)
        data.flush()
        // TODO close connection?
        //out.close()
        //data.close()
        //socket.close()
    }

    private fun authenticate(header: MutableMap<String, String>) {
        // Check url
        val check = sec!!.checkAuthorization(header["Authorization"]!!)
        if (check.first == 0) {
            val sessionID = sec!!.addSession(check.second)
            startSession(sessionID)
        }
        // send reload on false password
        else if (check.first == 1) {
            restartSession()
        }
        else {
            HTTPTransport().locked().transport(out)
            shutdown()
        }
    }

    private fun stopSession(sid: String) {
        sec!!.removeSession(sid)
        home()
    }

    private fun login() {
        val path = sec!!.login
        val ip = config.findObjectAttribute("server", "ip_address") as String
        val port = config.findObjectAttribute<String>("server", "https_port")
        out.println("HTTP/1.1 302 Found")
        out.println("Server: SaltApplication")
        out.println("Date: ${Date()}")
        out.println("Content-type: text/plain")
        out.println("Location: https://$ip:$port$path")
        out.println("Connection: Close")
        out.println()
        out.flush()
        shutdown()
    }

    private fun startSession(sessionID: String) {
        out.println("HTTP/1.1 204 No content")
        out.println("Server: SaltApplication")
        out.println("Date: ${Date()}")
        out.println("Set-Cookie: _sid=$sessionID; Secure; HttpOnly")
        out.println()
        out.flush()
    }

    private fun home() {
        out.println("HTTP/1.1 204 No content")
        out.println("Server: SaltApplication")
        out.println("Date: ${Date()}")
        out.println()
        out.flush()
    }

    private fun restartSession() {
        out.println("HTTP/1.1 403 Forbidden")
        out.println("Server: SaltApplication")
        out.println("Date: ${Date()}")
        out.println()
        out.flush()
        shutdown()
    }

    // return supported MIME Types
    private fun getContentType(fileRequested: File): String {
        return Files.probeContentType(fileRequested.toPath())
    }

    @Throws(IOException::class)
    private fun readFileData(file: File, model: Model?): Pair<ByteArray, Int> {
        val bytes = if (getContentType(file) == "text/html" && file.name.endsWith(".html")) {
            val raw = file.readText(Charsets.UTF_8)
            try {
                val lines = raw.split("\r\n").toMutableList()
                val site: String
                site = if (model != null) {
                    Webparse.parse(lines, model)
                } else {
                    val tmpSite = StringBuilder()
                    for (l in lines) {
                        tmpSite.append(l + "\r\n")
                    }
                    tmpSite.toString()
                }
                site.toByteArray(Charsets.UTF_8)
            }
            catch (ex: Exception) {
                log.warning(ExceptionsTools.exceptionToString(ex))
            }
        }
        else {
            val reader = BufferedInputStream(FileInputStream(file))
            val bytes = ByteArray(reader.available())
            reader.read(bytes)
            bytes
        }
        if (bytes is ByteArray) {
            val length = bytes.size
            return Pair(bytes, length)
        }
        else return Pair(ByteArray(0), 0)
    }

    private fun fileNotFound() {
        val file = File(WEB_ROOT, FILE_NOT_FOUND)
        val content = "text/html"
        val fileData = readFileData(file, null)
        sendHelper("HTTP/1.1 404 File Not Found",
                "Server: SaltApplication",
                content, fileData.second, fileData.first)
    }

    private fun redirect(path: String) {
        val ip = config.findObjectAttribute("server", "ip_address") as String
        val port = config.findObjectAttribute<String>("server", "https_port")
        out.println("HTTP/1.1 302 Found")
        out.println("Server: SaltApplication")
        out.println("Date: ${Date()}")
        out.println("Content-type: text/plain")
        out.println("Location: https://$ip:$port$path")
        out.println("Connection: Close")
        out.println()
        out.flush()
        shutdown()
    }

    fun shutdown() {
        listening = false
        inp.close()
        out.close()
        data.close()
        socket.close()
    }

    private fun readRequest(): Request {
        val req = inp.readLine()
        val reqLs = req.split(" ")
        val method = reqLs[0].toUpperCase()
        val pathParam = reqLs[1].split(Regex.fromLiteral("?"), 2)
        val pathOrFile = pathParam[0].split(".")
        val path = pathOrFile[0]
        val file = if (pathOrFile.size == 2) {
            path + "." + pathOrFile[1]
        } else ""
        var params = mutableMapOf<String, String>()
        if (pathParam.size > 1) {
            val parLs = pathParam[1].split("&")
            params = parLs.map {
                val tmp = it.split("=")
                tmp[0] to tmp[1]
            }.toMap().toMutableMap()
        }
        val ipaddress = WebTools.getIPAdress(config)
        return Request(method, path, file, params, ipaddress)
    }

    private fun readHeader(): MutableMap<String, String> {
        val header = mutableMapOf<String, String>()
        var adder: String = ""
        do {
            try {
                adder = inp.readLine()
            }
            catch (ex: Exception) {
                log.warning(ExceptionsTools.exceptionToString(ex))
            }
            if (adder != "") {
                val data = adder.split(Regex.fromLiteral(":"), 2)
                header[data[0]] = data[1].trim()
            }

        } while (adder != "")
        return header
    }

    private fun readBody(request: Request, header: MutableMap<String, String>) {
        if (header.containsKey("Content-Length") && header["Content-Length"] != "0") {
            val length: Int = header["Content-Length"]!!.toInt()
            val data = CharArray(length)
            inp.read(data, 0, length)
            val raw = String(data) // TODO HttpBody support whitespace?
            val fine = raw.split("&")
            for (attributes in fine) {
                if (attributes.contains("=")) {
                    val finest = URLDecoder.decode(attributes, "UTF-8").split("=")
                    if (finest.size == 2)
                        request.params[finest[0]] = finest[1]
                }
            }
        }
    }

    private fun getSID(cookieRaw: String): String {
        val begin = cookieRaw.indexOf("_sid=")
        val tmp = cookieRaw.substring(begin+5)
        val tmp2 = tmp.split(" ")
        return tmp2[0]
    }

}