package app.protocol

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import java.net.SocketAddress

/** This Netty Handler is an all-in-one handler to catch any exception related to connecting, reading, or writing.
 *  It is situated at the end of the tunnel (Pipeline's tail) so any exception caught along the way will propagate
 *  its way up until this point. Therefore, we don't need any try/catch blocks or other handlers. Just this one.
 */
class ExceptionHandler : ChannelDuplexHandler() {
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        /** Uncaught exceptions from inbound handlers will propagate up to this handler */
        cause.printStackTrace()
    }

    override fun connect(
        ctx: ChannelHandlerContext,
        remoteAddress: SocketAddress?,
        localAddress: SocketAddress?,
        promise: ChannelPromise
    ) {
        ctx.connect(
            remoteAddress,
            localAddress,
            promise.addListener(ChannelFutureListener { future ->
                if (!future.isSuccess) {
                    val c = future.cause()
                    c.printStackTrace()
                }
            })
        )
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        ctx.write(msg, promise.addListener(ChannelFutureListener { future ->
            if (!future.isSuccess) {
                val c = future.cause()
                c.printStackTrace()
            }
        }))
    }
}