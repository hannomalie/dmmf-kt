package org.example

import kotlinx.serialization.json.Json

data class JsonString(val value: String)
data class HttpRequest(val action: String, val uri: String, val body: JsonString)
data class HttpResponse(val httpStatusCode: Int, val body: JsonString)

typealias PlaceOrderApi = suspend (HttpRequest) -> HttpResponse

inline fun <reified T> serializeJson(it: T): String = Json.encodeToString(it)
inline fun <reified T> deserializeJson(it: String): T = Json.decodeFromString(it)

internal fun checkProductExists(productCode: ProductCode) = true
internal fun checkAddressExists(unvalidatedAddress: UnvalidatedAddress) {

}