package org.example

import com.github.michaelbull.result.Result
import java.net.URL

data class UnvalidatedCustomerInfo(
    val firstName: String,
    val lastName: String,
    val emailAddress: String,
    val vipStatus: String,
)

data class UnvalidatedAddress(
    val addressLine1: String,
    val addressLine2: String,
    val addressLine3: String,
    val addressLine4: String,
    val city: String,
    val zipCode: String,
    val state: String,
    val country: String,
)

data class UnvalidatedOrderLine(
    val orderLineId: String,
    val productCode: String,
    val quantity: Float
)

data class UnvalidatedOrder(
    val orderId: String,
    val customerInfo: UnvalidatedCustomerInfo,
    val shippingAddress: UnvalidatedAddress,
    val billingAddress: UnvalidatedAddress,
    val lines: List<UnvalidatedOrderLine>,
    val promotionCode: String
)

data class OrderAcknowledgementSent(val orderId: OrderId, val emailAddress: EmailAddress)
data class OrderPlaced(val priceOrder: PricedOrder)

data class ShippableOrderLine(
    val productCode: ProductCode,
    val quantity: OrderQuantity,
)

data class ShippableOrderPlaced(
    val orderId: OrderId,
    val shippingAddress: Address,
    val shipmentLines: List<ShippableOrderLine>,
    val pdf: PdfAttachment,
)

data class BillableOrderPlaced(
    val orderId: OrderId,
    val billingAddress: Address,
    val amountToBill: BillingAmount,
)

sealed interface PlaceOrderEvent
data class ShippableOrderPlacedEvent(val shippableOrderPlaced: ShippableOrderPlaced): PlaceOrderEvent
data class BillableOrderPlacedEvent(val billableOrderPlaced: BillableOrderPlaced): PlaceOrderEvent
data class AcknowledgmentSentEvent(val orderAcknowledgmentSent: OrderAcknowledgementSent): PlaceOrderEvent


data class ValidationError(val value: String)
data class PricingError(val value: String)

data class ServiceInfo(
    val name: String,
    val endpoint: URL,
)

data class RemoteServiceError(
    val service: ServiceInfo,
    val exception: RuntimeException,
)

sealed interface PlaceOrderError
data class Validation(val validationError: ValidationError)
data class Pricing(val pricingError: PricingError)
data class RemoteService(val remoteServiceError: RemoteServiceError)


typealias PlaceOrder = suspend (UnvalidatedOrder) -> Result<List<PlaceOrderEvent>,PlaceOrderError>