package org.example

import com.github.michaelbull.result.Result

internal fun interface CheckProductCodeExists { operator fun invoke(productCode: ProductCode): Boolean }

sealed interface AddressValidationError
data object InvalidFormat: AddressValidationError
data object AddressNotFound: AddressValidationError

data class CheckedAddress(val checkedAddress: UnvalidatedAddress)

internal fun interface CheckAddressExists { suspend operator fun invoke(unvalidatedAddress: UnvalidatedAddress): Result<CheckedAddress, AddressValidationError> }

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

typealias ValidateOrder =
            suspend (CheckProductCodeExists, CheckAddressExists, UnvalidatedOrder) -> Result<ValidatedOrder, ValidationError>
typealias GetProductPrice = (ProductCode) -> Price
typealias TryGetProductPrice = (ProductCode) -> Price?
typealias GetPricingFunction = (PricingMethod) -> GetProductPrice
typealias GetStandardPrices = () -> GetProductPrice
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

typealias PriceOrder = (GetPricingFunction, ValidateOrder) -> Result<PricedOrder, PricingError>

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

typealias CalculateShippingCost = (PricedOrder) -> Price
typealias AddShippingInfoToOrder = (CalculateShippingCost, PricedOrder) -> PricedOrderWithShippingMethod

typealias FreeVipShipping = (PricedOrderWithShippingMethod) -> PricedOrderWithShippingMethod

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

typealias SendOrderAcknowledgment = (OrderAcknowledgment) -> SendResult

typealias AcknowledgeOrder = (CreateOrderAcknowledgmentLetter, SendOrderAcknowledgment, PricedOrderWithShippingMethod) -> OrderAcknowledgmentSent?

typealias CreateEvents = (PricedOrder, OrderAcknowledgmentSent?) -> List<PlaceOrderEvent>