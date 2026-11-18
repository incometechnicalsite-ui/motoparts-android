package com.motoparts.usa

import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class ApiError(val error: String?)
data class Brand(val id: Int, val name: String, val slug: String, val logo_url: String?)
data class Category(val id: Int, val name: String, val slug: String, val group_name: String)
data class BikeModel(val id: Int, val name: String, val year_from: Int, val year_to: Int?)
data class ProductSummary(
    val id: String, val name: String, val sku: String, val part_brand: String?, val condition: String,
    val price_usd: Double?, val price_bdt: Double?, val price_is_estimated: Boolean, val stock: Int,
    val seller_name: String, val category: String, val image_url: String?, val rating: String?,
    val in_stock: Boolean
)
data class Img(val id: String, val url: String)
data class Compat(val brand: String, val model: String, val year_from: Int, val year_to: Int?)
data class ProductDetail(
    val id: String, val name: String, val sku: String, val part_brand: String?, val condition: String,
    val description: String?, val price_usd: Double?, val price_bdt: Double?, val price_is_estimated: Boolean,
    val stock: Int, val shipping_info: String?, val seller_name: String, val category: String,
    val rating: String?, val review_count: Int?, val images: List<Img>, val compatibility: List<Compat>
)
data class User(val id: String, val email: String, val full_name: String, val role: String)
data class AuthResp(val user: User, val token: String)
data class LoginReq(val email: String, val password: String)
data class RegisterReq(val email: String, val password: String, val full_name: String)
data class Address(
    val id: String, val full_name: String, val phone: String, val line1: String, val line2: String?,
    val city: String, val state: String?, val postal_code: String?, val country: String
)
data class AddressReq(
    val full_name: String, val phone: String, val line1: String, val city: String,
    val postal_code: String?, val country: String
)
data class OrderItemReq(val product_id: String, val quantity: Int)
data class OrderReq(val address_id: String, val currency: String, val items: List<OrderItemReq>)
data class OrderSummary(
    val id: String, val currency: String, val subtotal_usd: String, val shipping_usd: String,
    val total_usd: String, val status: String, val created_at: String
)
data class OrderItem(
    val id: String, val product_name: String, val sku: String, val quantity: Int,
    val unit_price_usd: String, val status: String
)
data class OrderDetail(
    val id: String, val currency: String, val subtotal_usd: String, val shipping_usd: String,
    val total_usd: String, val status: String, val created_at: String, val items: List<OrderItem>
)
data class PayResp(val checkout_url: String?)

interface Api {
    @GET("brands") suspend fun brands(): List<Brand>
    @GET("categories") suspend fun categories(): List<Category>
    @GET("brands/{id}/models") suspend fun models(@Path("id") id: Int): List<BikeModel>
    @GET("products") suspend fun products(
        @Query("search") search: String? = null,
        @Query("category_id") categoryId: Int? = null,
        @Query("model_id") modelId: Int? = null,
        @Query("year") year: Int? = null
    ): List<ProductSummary>
    @GET("products/{id}") suspend fun product(@Path("id") id: String): ProductDetail
    @POST("auth/login") suspend fun login(@Body b: LoginReq): AuthResp
    @POST("auth/register") suspend fun register(@Body b: RegisterReq): AuthResp
    @GET("addresses") suspend fun addresses(): List<Address>
    @POST("addresses") suspend fun addAddress(@Body b: AddressReq): Address
    @POST("orders") suspend fun createOrder(@Body b: OrderReq): OrderSummary
    @GET("orders") suspend fun orders(): List<OrderSummary>
    @GET("orders/{id}") suspend fun order(@Path("id") id: String): OrderDetail
    @POST("orders/{id}/cancel") suspend fun cancel(@Path("id") id: String): Map<String, Any>
    @POST("payments/orders/{id}/pay") suspend fun pay(@Path("id") id: String): PayResp
}

object Net {
    lateinit var api: Api
    fun init(session: Session) {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val b = chain.request().newBuilder()
            session.token?.let { b.header("Authorization", "Bearer $it") }
            chain.proceed(b.build())
        }.build()
        api = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api::class.java)
    }
}

fun errorMessage(e: Throwable): String = when (e) {
    is HttpException -> {
        val body = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
        val msg = try { Gson().fromJson(body, ApiError::class.java)?.error } catch (_: Exception) { null }
        msg ?: "Request failed (${e.code()})"
    }
    is java.io.IOException -> "Cannot reach the server. Check your internet connection."
    else -> e.message ?: "Something went wrong"
}
