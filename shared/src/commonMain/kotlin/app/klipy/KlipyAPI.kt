package app.klipy

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query

interface KlipyAPI {

    /* ---- GIFs ---- */

    @GET("gifs/search")
    suspend fun searchGifs(
        @Query("q") query: String,
        @Query("per_page") perPage: Int,
        @Query("customer_id") customerId: String,
    ): GifSearchResponse

    @GET("gifs/trending")
    suspend fun trendingGifs(
        @Query("per_page") perPage: Int,
        @Query("customer_id") customerId: String,
    ): GifSearchResponse

    @GET("gifs/recent/{customer_id}")
    suspend fun recentGifs(
        @Path("customer_id") customerId: String,
        @Query("per_page") perPage: Int,
    ): GifSearchResponse

    /* ---- Stickers ---- */

    @GET("stickers/search")
    suspend fun searchStickers(
        @Query("q") query: String,
        @Query("per_page") perPage: Int,
        @Query("customer_id") customerId: String,
    ): GifSearchResponse

    @GET("stickers/trending")
    suspend fun trendingStickers(
        @Query("per_page") perPage: Int,
        @Query("customer_id") customerId: String,
    ): GifSearchResponse

    @GET("stickers/recent/{customer_id}")
    suspend fun recentStickers(
        @Path("customer_id") customerId: String,
        @Query("per_page") perPage: Int,
    ): GifSearchResponse

    /* ---- Share trigger (analytics) ---- */

    @POST("gifs/share/{slug}")
    suspend fun shareGif(@Path("slug") slug: String)

    @POST("stickers/share/{slug}")
    suspend fun shareSticker(@Path("slug") slug: String)
}
