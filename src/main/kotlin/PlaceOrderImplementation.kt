package org.example

import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import org.example.StateType.*

fun toCustomerInfo(unvalidatedCustomerInfo: UnvalidatedCustomerInfo) = binding {
    val firstName = String50.create("FirstName", unvalidatedCustomerInfo.firstName).mapError { ValidationError(it) }.bind()
    val lastName = String50.create("LastName", unvalidatedCustomerInfo.lastName).mapError { ValidationError(it) }.bind()
    val emailAddress = EmailAddress.create("EmailAddress", unvalidatedCustomerInfo.emailAddress).mapError { ValidationError(it) }.bind()
    val vipStatus = VipStatus.create("vipStatus", unvalidatedCustomerInfo.vipStatus).mapError { ValidationError(it) }.bind()

    CustomerInfo(
        name = PersonalName(firstName, lastName),
        emailAddress = emailAddress,
        vipStatus = vipStatus,
    )
}

fun toAddress(checkedAddress: CheckedAddress) = binding {
    val addressLine1 = String50.create("AddressLine1", checkedAddress.checkedAddress.addressLine1).mapError { ValidationError(it) }.bind()
    val addressLine2 = String50.createOption("AddressLine2", checkedAddress.checkedAddress.addressLine2).mapError { ValidationError(it) }.bind()
    val addressLine3 = String50.createOption("AddressLine3", checkedAddress.checkedAddress.addressLine3).mapError { ValidationError(it) }.bind()
    val addressLine4 = String50.createOption("AddressLine4", checkedAddress.checkedAddress.addressLine4).mapError { ValidationError(it) }.bind()
    val city = String50.create("City", checkedAddress.checkedAddress.city).mapError { ValidationError(it) }.bind()
    val zipCode = ZipCode.create("ZipCode", checkedAddress.checkedAddress.zipCode).mapError { ValidationError(it) }.bind()
    val state = UsStateCode.create("State", checkedAddress.checkedAddress.state).mapError { ValidationError(it) }.bind()
    val country = String50.create("Country", checkedAddress.checkedAddress.country).mapError { ValidationError(it) }.bind()
    val address = Address(
        addressLine1 = addressLine1,
        addressLine2 = addressLine2,
        addressLine3 = addressLine3,
        addressLine4 = addressLine4,
        city = City(city),
        zipCode = zipCode,
        state = State(state),
        country = country,
    )
    address
}

internal suspend fun toCheckedAddress(checkAddress: CheckAddressExists, address: UnvalidatedAddress): Result<CheckedAddress, ValidationError> = coroutineBinding {
    checkAddress(address).mapError {
        when(it) {
            AddressNotFound -> ValidationError("Address not found")
            InvalidFormat -> ValidationError("Address has bad format")
        }
    }.bind()
}

fun toOrderId(orderId: String): Result<OrderId, ValidationError> = OrderId.create("OrderId", orderId).mapError {
    ValidationError(it)
}

fun toOrderLineId(orderId: String): Result<OrderLineId, ValidationError> = OrderLineId.create("OrderLineId", orderId).mapError { ValidationError(it) }

fun toProductCode(checkProductCodeExists: CheckProductCodeExists, productCode: String): Result<ProductCode, ValidationError> = binding {
    fun checkProduct(productCode: ProductCode) = if(checkProductCodeExists(productCode)) Ok(productCode) else Err(ValidationError("Invalid: $productCode"))

    checkProduct(ProductCode.create("ProductCode", productCode).mapError { ValidationError(it) }.bind()).bind()
}

fun toOrderQuantity(productCode: ProductCode, quantity: Number) = OrderQuantity.create("OrderQuantity", productCode, quantity).mapError {
    ValidationError(it)
}

fun toValidatedOrderLine(checkProductExists: CheckProductCodeExists, unvalidatedOrderLine: UnvalidatedOrderLine) = binding {
    val orderLineId = toOrderLineId(unvalidatedOrderLine.orderLineId).bind()
    val productCode = toProductCode(checkProductExists, unvalidatedOrderLine.productCode).bind()
    val quantity = toOrderQuantity(productCode, unvalidatedOrderLine.quantity).bind()
    val validatedOrderLine = ValidatedOrderLine(
        orderLineId = orderLineId,
        productCode = productCode,
        quantity = quantity,
    )
    validatedOrderLine
}

val validateOrder = ValidateOrder { checkProductCodeExists: CheckProductCodeExists, checkAddressExists: CheckAddressExists, unvalidatedOrder: UnvalidatedOrder ->
    suspend fun validateOrder(
        checkProductCodeExists: CheckProductCodeExists,
        checkAddressExists: CheckAddressExists,
        unvalidatedOrder: UnvalidatedOrder
    ) =
        coroutineBinding {
            val orderId = toOrderId(unvalidatedOrder.orderId).bind()
            val customerInfo = toCustomerInfo(unvalidatedOrder.customerInfo).bind()
            val checkedShippingAddress =
                toCheckedAddress(checkAddressExists, unvalidatedOrder.shippingAddress).bind()
            val shippingAddress = toAddress(checkedShippingAddress).bind()
            val checkedBillingAddress = toCheckedAddress(checkAddressExists, unvalidatedOrder.billingAddress).bind()
            val billingAddress = toAddress(checkedBillingAddress).bind()
            val lines = unvalidatedOrder.lines.map {
                toValidatedOrderLine(checkProductExists, it).map { validatedOrderLine ->
                    if (checkProductCodeExists(validatedOrderLine.productCode)) {
                        Ok(validatedOrderLine)
                    } else {
                        Err(ValidationError("Invalid: ${validatedOrderLine.productCode}")) // TODO: Not sure if this is exactly what the original code does
                    }
                }.bind()
            }.combine().bind()
            val pricingMethod = createPricingMethod(unvalidatedOrder.promotionCode)

            val validatedOrder = ValidatedOrder(
                orderId = orderId,
                customerInfo = customerInfo,
                shippingAddress = shippingAddress,
                billingAddress = billingAddress,
                lines = lines,
                pricingMethod = pricingMethod,
            )
            validatedOrder
        }

    validateOrder(checkProductCodeExists, checkAddressExists, unvalidatedOrder)
}

fun toPricedOrderLine(getProductPrice: GetProductPrice, validatedOrderLine:ValidatedOrderLine) = binding {
    val qty = validatedOrderLine.quantity.value
    val price = getProductPrice(validatedOrderLine.productCode)
    val linePrice = price.multiply(qty).mapError { PricingError(it) }.bind()
    val pricedLine = PricedOrderProductLine(
        orderLineId = validatedOrderLine.orderLineId,
        productCode = validatedOrderLine.productCode,
        quantity = validatedOrderLine.quantity,
        linePrice = linePrice,
    )
    ProductLine(pricedLine)
}

fun addCommentLine(pricingMethod: PricingMethod, lines: List<PricedOrderLine>) = when(pricingMethod) {
    Standard -> lines
    is Promotion -> {
        lines + CommentLine("Applied promotion ${pricingMethod.promotionCode}")
    }
}

fun getLinePrice(line: PricedOrderLine): Price = when(line) {
    is ProductLine -> line.pricedOrderProductLine.linePrice
    is CommentLine -> Price.unsafeCreate(0f)
}

val priceOrder: PriceOrder
    get() = PriceOrder { getPricingFunction: GetPricingFunction, validatedOrder: ValidatedOrder ->
        val getProductPrice = getPricingFunction(validatedOrder.pricingMethod)
        binding {
            val lines =
                validatedOrder.lines.map { lines -> toPricedOrderLine(getProductPrice, lines) }.combine().map { lines ->
                    addCommentLine(validatedOrder.pricingMethod, lines)
                }.bind()

            val amountToBill = BillingAmount.create(BillingAmount.sumPrices(lines.map { getLinePrice(it) }))
                .mapError { PricingError(it) }.bind()

            val pricedOrder = PricedOrder(
                orderId = validatedOrder.orderId,
                customerInfo = validatedOrder.customerInfo,
                shippingAddress = validatedOrder.shippingAddress,
                billingAddress = validatedOrder.billingAddress,
                lines = lines,
                amountToBill = amountToBill,
                pricingMethod = validatedOrder.pricingMethod,
            )
            pricedOrder
        }
    }

enum class StateType { UsLocalState, UsRemoteState, International }

val Address.stateType: StateType
    get() = if (country.value == "US") {
        when (state.code.value) {
            "CA", "OR", "AZ", "NV" -> UsLocalState
            else -> UsRemoteState
        }
    } else {
        International
    }

val calculateShippingCost: CalculateShippingCost
    get() = CalculateShippingCost { pricedOrder ->
        when (pricedOrder.shippingAddress.stateType) {
            UsLocalState -> 5f
            UsRemoteState -> 10f
            International -> 20f
        }.let { Price.unsafeCreate(it) }
    }

val addShippingInfoToOrder: AddShippingInfoToOrder
    get() = AddShippingInfoToOrder { calculateShippingCost, pricedOrder ->
        val shippingInfo = ShippingInfo(
            shippingMethod = ShippingMethod.Fedex24,
            shippingCost = calculateShippingCost(pricedOrder),
        )
        PricedOrderWithShippingMethod(shippingInfo, pricedOrder)
    }

/// Update the shipping cost if customer is VIP
val freeVipShipping = FreeVipShipping { pricedOrderWithShippingMethod ->
    val updatedShippingInfo = when(pricedOrderWithShippingMethod.pricedOrder.customerInfo.vipStatus) {
        VipStatus.Normal -> pricedOrderWithShippingMethod
        VipStatus.Vip -> {
            pricedOrderWithShippingMethod
                .copy(shippingInfo = pricedOrderWithShippingMethod.shippingInfo.copy(shippingCost = Price.unsafeCreate(0f), shippingMethod = ShippingMethod.Fedex24))
        }
    }
    pricedOrderWithShippingMethod.copy(shippingInfo = updatedShippingInfo.shippingInfo, pricedOrder = updatedShippingInfo.pricedOrder)
}


val acknowledgeOrder = AcknowledgeOrder { createOrderAcknowledgmentLetter, sendAcknowledgment, pricedOrderWithShipping ->
    val pricedOrder = pricedOrderWithShipping.pricedOrder

    val letter = createOrderAcknowledgmentLetter(pricedOrderWithShipping)
    val acknowledgment = OrderAcknowledgment(
        emailAddress = pricedOrder.customerInfo.emailAddress,
        letter = letter,
    )

    when(sendAcknowledgment(acknowledgment)) {
        SendResult.Sent -> OrderAcknowledgementSent(pricedOrder.orderId, pricedOrder.customerInfo.emailAddress)
        SendResult.NotSent -> null
    }
}

fun makeShipmentLine(line: PricedOrderLine): ShippableOrderLine? = when(line) {
    is ProductLine -> ShippableOrderLine(line.pricedOrderProductLine.productCode, line.pricedOrderProductLine.quantity)
    is CommentLine -> null
}

fun createShippingEvent(placedOrder: PricedOrder) = ShippableOrderPlaced(
    orderId = placedOrder.orderId,
    shippingAddress = placedOrder.shippingAddress,
    shipmentLines = placedOrder.lines.mapNotNull { makeShipmentLine(it) },
    pdf = PdfAttachment(
        name = "Order${(placedOrder.orderId.value)}.pdf",
        bytes = ByteArray(1), // This is probably not what the OG code does
    ),
)
fun createBillingEvent(placedOrder: PricedOrder): BillableOrderPlaced? {
    val billingAmount = placedOrder.amountToBill.value
    return if(billingAmount > 0) {
        BillableOrderPlaced(
            orderId = placedOrder.orderId,
            billingAddress = placedOrder.billingAddress,
            amountToBill = placedOrder.amountToBill,
        )
    } else {
        null
    }
}

fun <T> listOfOption(element: T?) = listOfNotNull(element)

val createEvents = CreateEvents { pricedOrder: PricedOrder, orderAcknowledgementSent: OrderAcknowledgementSent? ->
    val acknowledgmentEvents = listOfOption(orderAcknowledgementSent).map { AcknowledgmentSentEvent(OrderAcknowledgementSent(it.orderId, it.emailAddress)) }
    val shippingEvents = listOf(ShippableOrderPlacedEvent(createShippingEvent(pricedOrder)))
    val billingEvents = listOfNotNull(createBillingEvent(pricedOrder)).map { BillableOrderPlacedEvent(BillableOrderPlaced(it.orderId, it.billingAddress, it.amountToBill)) }

    buildList {
        addAll(acknowledgmentEvents)
        addAll(shippingEvents)
        addAll(billingEvents)
    }
}

val placeOrder: suspend (CheckProductCodeExists, CheckAddressExists, GetPricingFunction, CalculateShippingCost, CreateOrderAcknowledgmentLetter, SendOrderAcknowledgment, UnvalidatedOrder) -> Result<List<PlaceOrderEvent>, PlaceOrderError> = {
    checkProductExists: CheckProductCodeExists,
    checkAddressExists: CheckAddressExists,
    getProductPrice: GetPricingFunction,
    calculateShippingCost: CalculateShippingCost,
    createOrderAcknowledgmentLetter: CreateOrderAcknowledgmentLetter,
    sendOrderAcknowledgment: SendOrderAcknowledgment,
    unvalidatedOrder: UnvalidatedOrder ->

    coroutineBinding {
        val validatedOrder = validateOrder(checkProductExists, checkAddressExists, unvalidatedOrder).mapError { Validation(it) }.bind()
        val pricedOrder = priceOrder(getProductPrice, validatedOrder).mapError { Pricing(it) }.bind()
        val pricedOrderWithShipping = freeVipShipping(addShippingInfoToOrder(calculateShippingCost, pricedOrder))

        val acknowledgementOption = acknowledgeOrder(createOrderAcknowledgmentLetter, sendOrderAcknowledgment, pricedOrderWithShipping)
        val events = createEvents(pricedOrder, acknowledgementOption)
        events
    }
}
