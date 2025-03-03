package org.example

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import kotlinx.serialization.json.Json

data class JsonString(val value: String)
data class HttpRequest(val action: String, val uri: String, val body: JsonString)
data class HttpResponse(val httpStatusCode: Int, val body: JsonString)

typealias PlaceOrderApi = suspend (HttpRequest) -> HttpResponse

inline fun <reified T> serializeJson(it: T): JsonString = JsonString(Json.encodeToString(it))
inline fun <reified T> deserializeJson(it: String): T = Json.decodeFromString(it)

internal val checkProductExists: CheckProductCodeExists = CheckProductCodeExists {
    true
}
// original returns async result, but here, no suspending keyword necessary
internal fun checkAddressExists(unvalidatedAddress: UnvalidatedAddress) = CheckedAddress(unvalidatedAddress)

internal fun getStandardPrices() : GetStandardPrices = { _ ->
    Price.unsafeCreate(10f)
}

internal fun getPromotionPrices(): GetPromotionPrices {
    val halfPricePromotion: TryGetProductPrice = { productCode ->
        if(productCode.value == "ONSALE") {
            Price.unsafeCreate(5f)
        } else {
            null
        }
    }

    val quarterPricePromotion: TryGetProductPrice = { productCode ->
        if(productCode.value == "ONSALE") {
            Price.unsafeCreate(2.5f)
        }
        else {
            null
        }
    }

    val noPromotion : TryGetProductPrice = { null }

    return { promotionCode: PromotionCode ->
        when(promotionCode.value) {
            "HALF" -> halfPricePromotion
            "QUARTER" -> quarterPricePromotion
            else -> noPromotion
        }
    }
}


internal fun getPricingFunction(): GetPricingFunction = getPricingFunction(getStandardPrices(), getPromotionPrices())

internal fun createOrderAcknowledgmentLetter() : CreateOrderAcknowledgmentLetter = {
    HtmlString("some text")
}

internal fun sendOrderAcknowledgment() : SendOrderAcknowledgment = {
    SendResult.Sent
}


// -------------------------------
// workflow
// -------------------------------

/// This function converts the workflow output into a HttpResponse
// Here's a typo, but it was like that in the original source, so I'll leave it :)
fun workflowResultToHttpReponse(result: Result<List<PlaceOrderEvent>, PlaceOrderError>) {
    result.fold(
        success = { events ->
            val dtos = events.map(PlaceOrderEventDto::fromDomain)
            val json = serializeJson(dtos)
            HttpResponse(200, json)
        },
        failure = { err ->
            val dto = PlaceOrderErrorDto.fromDomain(err)
            val json = serializeJson(dto)
            HttpResponse(401, json)
        }
    )

}

//suspend fun placeOrderApi(): PlaceOrderApi = { request ->
//    val orderFormJson = request.body
//    val orderForm = deserializeJson<OrderFormDto>(orderFormJson)
//
//    val unvalidatedOrder = OrderFormDto.toUnvalidatedOrder(orderForm)
//
//    val workflow = placeOrder(
//        checkProductExists,
//        checkAddressExists,
//        getPricingFunction,
//        calculateShippingCost,
//        createOrderAcknowledgmentLetter ,
//        sendOrderAcknowledgment,
//    )
//
//    val asyncResult = workflow(unvalidatedOrder)
//
//    return asyncResult.map(::workflowResultToHttpReponse)
//}