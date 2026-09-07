package io.github.meko123456.syncbeats.core.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

class NewPipeDownloaderImpl private constructor(
    private val client: OkHttpClient,
) : Downloader() {

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .method(
                httpMethod,
                dataToSend?.toRequestBody(null, 0, dataToSend.size),
            )
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        headers.forEach { (name, values) ->
            requestBuilder.removeHeader(name)
            values.forEach { value -> requestBuilder.addHeader(name, value) }
        }

        val okResponse = client.newCall(requestBuilder.build()).execute()
        if (okResponse.code == 429) {
            okResponse.close()
            throw ReCaptchaException("reCaptcha required", url)
        }
        val body = okResponse.body
        val bodyString = body?.string() ?: ""
        val latestUrl = okResponse.request.url.toString()
        return Response(
            okResponse.code,
            okResponse.message,
            okResponse.headers.toMultimap(),
            bodyString,
            latestUrl,
        )
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        @Volatile
        private var INSTANCE: NewPipeDownloaderImpl? = null

        fun instance(): NewPipeDownloaderImpl =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NewPipeDownloaderImpl(
                    OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build()
                ).also { INSTANCE = it }
            }
    }
}
