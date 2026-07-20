package io.kanro.idea.plugin.protobuf.grpc.reflection

import com.bybutter.sisyphus.protobuf.dynamic.DynamicFileSupport
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.MethodDescriptor
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Discovers gRPC services from a server that supports
 * [gRPC Server Reflection](https://github.com/grpc/grpc/blob/master/doc/server-reflection.md).
 *
 * Without ServerReflection, gRPC request execution requires local `.proto` files.
 * With ServerReflection, the IDE can auto-discover services deployed on the server,
 * enabling gRPC request testing without any local `.proto` source.
 *
 * This class talks the reflection protocol at the wire level using raw protobuf encoding,
 * so it doesn't need the `grpc-services` proto dependency.
 *
 * ### Usage
 * ```kotlin
 * val service = GrpcReflectionService.fromServer("localhost", 9090)
 * if (service.supportsReflection()) {
 *     val reflection = service.buildReflection()
 *     // reflection can marshal/unmarshal all discovered services
 * }
 * service.shutdown()
 * ```
 */
class GrpcReflectionService private constructor(
    private val host: String,
    private val port: Int,
) {
    private val channel: ManagedChannel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build()

    /** Lists all service names registered on the server (e.g. "package.ServiceName"). */
    fun listServices(): List<String> {
        val request = ReflectionRequestCompanion.listServices()
        val response = callReflection(request)
        return response.parseServiceNames()
    }

    /** Gets the file descriptor bytes for a specific service, or null if unavailable. */
    fun resolveFileDescriptor(serviceName: String): ByteArray? {
        val request = ReflectionRequestCompanion.fileContainingService(serviceName)
        val response = callReflection(request)
        return response.parseFirstFileDescriptor()
    }

    /**
     * Builds a `LocalProtoReflection`-compatible set of dynamic file descriptors
     * for all services discovered from the server.
     *
     * Note: Due to the complexity of building a full LocalProtoReflection from
     * dynamically loaded descriptors (which requires descriptor dependency resolution),
     * this method returns a list of `Descriptors.FileDescriptor` objects.
     *
     * These can be converted to ProtoSupport via `DynamicFileSupport`.
     */
    fun buildDescriptors(): List<Descriptors.FileDescriptor> {
        val results = mutableListOf<Descriptors.FileDescriptor>()
        val known = mutableSetOf<String>()

        for (name in listServices()) {
            if (name in known) continue
            resolveFileDescriptor(name)?.let { bytes ->
                try {
                    val fdProto = DescriptorProtos.FileDescriptorProto.parseFrom(bytes)
                    val fd = Descriptors.FileDescriptor.buildFrom(fdProto, emptyArray())
                    results.add(fd)
                    known += name
                    known += fdProto.getDependencyList()
                } catch (_: Exception) {
                    // Skip services with unresolvable descriptors
                }
            }
        }
        return results
    }

    /** Returns true if the server supports gRPC Server Reflection. */
    fun supportsReflection(): Boolean {
        return try {
            listServices().isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun shutdown() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun callReflection(request: ByteArray): ByteArray {
        val call = channel.newCall(SERVER_REFLECTION_METHOD, io.grpc.CallOptions.DEFAULT)
        @Suppress("UNCHECKED_CAST")
        val listener = ResponseListener()
        call.start(listener, io.grpc.Metadata())
        call.sendMessage(request)
        call.halfClose()
        call.request(1)

        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (listener.response == null && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        call.cancel("done", null)
        return listener.response ?: ByteArray(0)
    }

    private class ResponseListener : io.grpc.ClientCall.Listener<ByteArray>() {
        var response: ByteArray? = null

        override fun onMessage(message: ByteArray) {
            if (response == null) response = message
        }

        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
    }

    companion object {
        private val SERVER_REFLECTION_METHOD = MethodDescriptor
            .newBuilder<ByteArray, ByteArray>()
            .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
            .setFullMethodName("/grpc.reflection.v1.ServerReflection/ServerReflectionInfo")
            .setRequestMarshaller(RawBytesMarshaller)
            .setResponseMarshaller(RawBytesMarshaller)
            .build()

        fun fromServer(host: String, port: Int): GrpcReflectionService {
            return GrpcReflectionService(host, port)
        }
    }
}

// --- Reflection protocol helpers ---

private object ReflectionRequestCompanion {
    fun listServices(): ByteArray = byteArrayOf(
        0x0a, 0x00,
        0x12, 0x00,
    )

    fun fileContainingService(serviceName: String): ByteArray {
        val nameBytes = serviceName.toByteArray(Charsets.UTF_8)
        return byteArrayOf(
            0x32, (nameBytes.size + 3).toByte(),
            0x0a, nameBytes.size.toByte(),
        ) + nameBytes
    }
}

private fun ByteArray.parseServiceNames(): List<String> {
    val result = mutableListOf<String>()
    var pos = 0
    while (pos < size) {
        val tag = get(pos).toInt() and 0xff
        val field = tag shr 3
        val wireType = tag and 0x07
        pos++

        val fieldLen = when (wireType) {
            0 -> { pos += varintLen(this, pos); 0 }
            1 -> { pos += 8; 8 }
            2 -> {
                val len = get(pos).toInt() and 0xff
                pos++
                len
            }
            5 -> { pos += 4; 4 }
            else -> 0
        }

        if (field == 4 && wireType == 2) {
            val end = pos + fieldLen
            while (pos < end) {
                val svcTag = get(pos).toInt() and 0xff
                val svcField = svcTag shr 3
                val svcWire = svcTag and 0x07
                pos++

                val svcLen = when (svcWire) {
                    0 -> { pos += varintLen(this, pos); 0 }
                    1 -> { pos += 8; 8 }
                    2 -> {
                        val len = get(pos).toInt() and 0xff
                        pos++
                        len
                    }
                    5 -> { pos += 4; 4 }
                    else -> 0
                }

                if (svcField == 1 && svcWire == 2) {
                    val name = String(copyOfRange(pos, pos + svcLen), Charsets.UTF_8)
                    result.add(name.substringAfterLast('/'))
                }
                pos += svcLen
            }
        } else {
            pos += fieldLen
        }
    }
    return result
}

private fun ByteArray.parseFirstFileDescriptor(): ByteArray? {
    var pos = 0
    while (pos < size) {
        val tag = get(pos).toInt() and 0xff
        val field = tag shr 3
        val wireType = tag and 0x07
        pos++

        val fieldLen = when (wireType) {
            0 -> { pos += varintLen(this, pos); 0 }
            1 -> { pos += 8; 8 }
            2 -> {
                val len = get(pos).toInt() and 0xff
                pos++
                len
            }
            5 -> { pos += 4; 4 }
            else -> 0
        }

        if (field == 5 && wireType == 2) {
            val end = pos + fieldLen
            while (pos < end) {
                val innerTag = get(pos).toInt() and 0xff
                val innerField = innerTag shr 3
                val innerWire = innerTag and 0x07
                pos++

                val innerLen = when (innerWire) {
                    0 -> { pos += varintLen(this, pos); 0 }
                    1 -> { pos += 8; 8 }
                    2 -> {
                        val len = get(pos).toInt() and 0xff
                        pos++
                        len
                    }
                    5 -> { pos += 4; 4 }
                    else -> 0
                }

                if (innerField == 1 && innerWire == 2) {
                    return copyOfRange(pos, pos + innerLen)
                }
                pos += innerLen
            }
        } else {
            pos += fieldLen
        }
    }
    return null
}

private fun varintLen(data: ByteArray, pos: Int): Int {
    var len = 0
    while (pos + len < data.size && data[pos + len].toInt() and 0x80 != 0) len++
    return len + 1
}

private object RawBytesMarshaller : MethodDescriptor.Marshaller<ByteArray> {
    override fun stream(value: ByteArray): InputStream = value.inputStream()
    override fun parse(stream: InputStream): ByteArray = stream.readBytes()
}
