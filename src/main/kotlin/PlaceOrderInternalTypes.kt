package org.example

import com.github.michaelbull.result.Result

fun interface CheckProductCodeExists { operator fun invoke(productCode: ProductCode): Boolean }

sealed interface AddressValidationError
data object InvalidFormat: AddressValidationError
data object AddressNotFound: AddressValidationError

data class CheckedAddress(val checkedAddress: UnvalidatedAddress)

fun interface CheckAddressExists { suspend operator fun invoke(unvalidatedAddress: UnvalidatedAddress): Result<CheckedAddress, AddressValidationError> }

sealed interface PricingMethod
data object Standard: PricingMethod
data class Promotion(val promotionCode: PromotionCode): PricingMethod

data class ValidatedOrderLine(
    val orderLineId: OrderLineId,
    val productCode: ProductCode,
    val quantity: OrderQuantity,
)

data class ValidatedOrder(
    val orderId: OrderId,
    val customerInfo: CustomerInfo,
    val shippingAddress: Address,
    val billingAddress: Address,
    val lines: List<ValidatedOrderLine>,
    val pricingMethod: PricingMethod,
)

fun interface ValidateOrder {
    suspend operator fun invoke(checkProductCodeExists: CheckProductCodeExists, checkAddressExists: CheckAddressExists, unvalidatedOrder: UnvalidatedOrder): Result<ValidatedOrder, ValidationError>
}
typealias GetProductPrice = (ProductCode) -> Price
typealias TryGetProductPrice = (ProductCode) -> Price?
typealias GetPricingFunction = (PricingMethod) -> GetProductPrice
typealias GetStandardPrices = (ProductCode) -> Price
typealias GetPromotionPrices = (PromotionCode) -> TryGetProductPrice


data class PricedOrderProductLine(
    val orderLineId: OrderLineId,
    val productCode: ProductCode,
    val quantity: OrderQuantity,
    val linePrice: Price,
)

sealed interface PricedOrderLine
data class ProductLine(val pricedOrderProductLine: PricedOrderProductLine): PricedOrderLine
data class CommentLine(val value: String): PricedOrderLine

data class PricedOrder(
    val orderId: OrderId,
    val customerInfo: CustomerInfo,
    val shippingAddress: Address,
    val billingAddress: Address,
    val amountToBill: BillingAmount,
    val lines: List<PricedOrderLine>,
    val pricingMethod: PricingMethod,
)

fun interface PriceOrder { operator fun invoke(getPricingFunction: GetPricingFunction, validatedOrder: ValidatedOrder): Result<PricedOrder, PricingError> }

enum class ShippingMethod {
    PostalService,
    Fedex24,
    Fedex48,
    Ups48;
}
data class ShippingInfo(
    val shippingMethod : ShippingMethod,
    val shippingCost : Price,
)

data class PricedOrderWithShippingMethod(
    val shippingInfo: ShippingInfo,
    val pricedOrder: PricedOrder,
)

fun interface CalculateShippingCost { operator fun invoke (pricedOrder: PricedOrder): Price }
fun interface AddShippingInfoToOrder { operator fun invoke(calculateShippingCost: CalculateShippingCost, pricedOrder: PricedOrder): PricedOrderWithShippingMethod }

fun interface FreeVipShipping { operator fun invoke(pricedOrderWithShippingMethod: PricedOrderWithShippingMethod): PricedOrderWithShippingMethod }

data class HtmlString (val htmlString: String)

data class OrderAcknowledgment(
    val emailAddress: EmailAddress,
    val letter: HtmlString,
)

typealias CreateOrderAcknowledgmentLetter = (PricedOrderWithShippingMethod) -> HtmlString

/// Send the order acknowledgement to the customer
/// Note that this does NOT generate an Result-type error (at least not in this workflow)
/// because on failure we will continue anyway.
/// On success, we will generate a OrderAcknowledgmentSent event,
/// but on failure we won't.

sealed interface SendResult {
    data object Sent: SendResult
    data object NotSent: SendResult
}

fun interface SendOrderAcknowledgment { operator fun invoke(orderAcknowledgment: OrderAcknowledgment): SendResult }

fun interface AcknowledgeOrder {
    operator fun invoke(
        createOrderAcknowledgmentLetter: CreateOrderAcknowledgmentLetter,
        sendOrderAcknowledgment: SendOrderAcknowledgment,
        pricedOrderWithShippingMethod: PricedOrderWithShippingMethod
    ): OrderAcknowledgementSent?
}

fun interface CreateEvents { operator fun invoke(pricedOrder: PricedOrder, orderAcknowledgementSent: OrderAcknowledgementSent?): List<PlaceOrderEvent> }