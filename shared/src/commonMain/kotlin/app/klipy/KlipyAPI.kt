package app.klipy

import de.jensklingenberg.ktorfit.http.GET

interface KlipyAPI {
    @GET("people/1/")
    suspend fun getPerson(): String
}