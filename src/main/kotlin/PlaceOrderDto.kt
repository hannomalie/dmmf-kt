package org.example

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import kotlinx.serialization.internal.NamedCompanion
import org.example.AddressDto.OrderFormLineDto
import org.example.AddressDto.OrderLineDto
import org.example.AddressDto.OrderLineDto.PricedOrderLineDto

object Utils {
    fun <T : Any> defaultIfNone(defaultValue: T, opt: T?): T = opt ?: defaultValue
}

data class CustomerInfoDto(
    val firstName : String,
    val lastName : String,
    val emailAddress : String,
    val vipStatus : String,
) {
    companion object {
        fun toUnvalidatedCustomerInfo(dto:CustomerInfoDto) : UnvalidatedCustomerInfo = UnvalidatedCustomerInfo(
            firstName = dto.firstName,
            lastName = dto.lastName,
            emailAddress = dto.emailAddress,
            vipStatus = dto.vipStatus,
        )
    }
}

fun toCustomerInfo(dto:CustomerInfoDto): Result<CustomerInfo, String> = binding {
    val first = String50.create("Firstname", dto.firstName).bind()
    val last = String50.create("Lastname", dto.lastName).bind()
    val email = EmailAddress.create("EmailAddress", dto.emailAddress).bind()
    val vipStatus = VipStatus.create("VipStatus", dto.vipStatus).bind()

    val name = PersonalName(first, last)
    val info = CustomerInfo(name, email, vipStatus)
    info
}

fun fromCustomerInfo(domainObj:CustomerInfo) = CustomerInfoDto(
    firstName = domainObj.name.firstName.value,
    lastName = domainObj.name.lastName.value,
    emailAddress = domainObj.emailAddress.value,
    vipStatus = domainObj.vipStatus.value,
)

//===============================================
// DTO for Address
//===============================================

data class AddressDto(
    val addressLine1: String,
    val addressLine2: String?,
    val addressLine3: String?,
    val addressLine4: String?,
    val city: String,
    val zipCode: String,
    val state: String,
    val country: String,
) {
    companion object {

        fun toUnvalidatedAddress(dto: AddressDto) = UnvalidatedAddress(
            addressLine1 = dto.addressLine1,
            addressLine2 = dto.addressLine2,
            addressLine3 = dto.addressLine3,
            addressLine4 = dto.addressLine4,
            city = dto.city,
            zipCode = dto.zipCode,
            state = dto.state,
            country = dto.country,
        )

        fun toAddress(dto: AddressDto): Result<Address, String> = binding {
            // get each (validated) simple type from the DTO as a success or failure
            val addressLine1 = String50.create("AddressLine1", dto.addressLine1).bind()
            val addressLine2 = String50.createOption("AddressLine2", dto.addressLine2).bind()
            val addressLine3 = String50.createOption("AddressLine3", dto.addressLine3).bind()
            val addressLine4 = String50.createOption("AddressLine4", dto.addressLine4).bind()
            val city = City(String50.create("City", dto.city).bind())
            val zipCode = ZipCode.create("ZipCode", dto.zipCode).bind()
            val state = State(UsStateCode.create("State", dto.state).bind())
            val country = String50.create("Country", dto.country).bind()

            val address = Address(
                addressLine1 = addressLine1,
                addressLine2 = addressLine2,
                addressLine3 = addressLine3,
                addressLine4 = addressLine4,
                city = city,
                zipCode = zipCode,
                state = state,
                country = country,
            )
            address
        }

        fun fromAddress(domainObj: Address) = AddressDto(
            addressLine1 = domainObj.addressLine1.value,
            addressLine2 = domainObj.addressLine2?.value,
            addressLine3 = domainObj.addressLine3?.value,
            addressLine4 = domainObj.addressLine4?.value,
            city = domainObj.city.value.value,
            zipCode = domainObj.zipCode.value,
            state = domainObj.state.code.value,
            country = domainObj.country.value,
        )
    }


//===============================================
// DTOs for OrderLines
//===============================================

    data class OrderFormLineDto(
        val orderLineId: String,
        val productCode: String,
        val quantity: Float,
    )

    /// Functions relating to the OrderLine DTOs
    object OrderLineDto {

        fun toUnvalidatedOrderLine(dto: OrderFormLineDto) = UnvalidatedOrderLine(
            orderLineId = dto.orderLineId,
            productCode = dto.productCode,
            quantity = dto.quantity,
        )


//===============================================
// DTOs for PricedOrderLines
//===============================================

        data class PricedOrderLineDto(
            val orderLineId: String?,
            val productCode: String?,
            val quantity: Float,
            val linePrice: Float,
            val comment: String,
        )
    }

    fun fromDomain(domainObj: PricedOrderLine): PricedOrderLineDto = when (domainObj) {
        is ProductLine -> PricedOrderLineDto(
            orderLineId = domainObj.pricedOrderProductLine.orderLineId.value,
            productCode = domainObj.pricedOrderProductLine.productCode.value,
            quantity = domainObj.pricedOrderProductLine.quantity.value.toFloat(),
            linePrice = domainObj.pricedOrderProductLine.linePrice.value,
            comment = "",
        )

        is CommentLine -> PricedOrderLineDto(
            orderLineId = null,
            productCode = null,
            quantity = 0f,
            linePrice = 0f,
            comment = domainObj.value,
        )
    }
}
//===============================================
// DTO for OrderForm
//===============================================

data class OrderFormDto(
    val orderId : String,
    val customerInfo : CustomerInfoDto,
    val shippingAddress : AddressDto,
    val billingAddress : AddressDto,
    val lines : List<OrderFormLineDto>,
    val promotionCode : String,
) {
    companion object {
        fun toUnvalidatedOrder(dto:OrderFormDto) = UnvalidatedOrder(
            orderId = dto.orderId,
            customerInfo = CustomerInfoDto.toUnvalidatedCustomerInfo(dto.customerInfo),
            shippingAddress = AddressDto.toUnvalidatedAddress(dto.shippingAddress),
            billingAddress = AddressDto.toUnvalidatedAddress(dto.billingAddress),
            lines = dto.lines.map { OrderLineDto.toUnvalidatedOrderLine(it) },
            promotionCode = dto.promotionCode,
        )
    }
}

//===============================================
// DTO for ShippableOrderPlaced event
//===============================================


data class ShippableOrderLineDto(
    val productCode: String,
    val quantity : Float,
)

data class ShippableOrderPlacedDto(
    val orderId: String,
    val shippingAddress: AddressDto,
    val shipmentLines: List<ShippableOrderLineDto>,
    val pdf: PdfAttachment,
) {
    companion object {
        fun fromShippableOrderLine(domainObj: ShippableOrderLine) = ShippableOrderLineDto(
            productCode = domainObj.productCode.value,
            quantity = domainObj.quantity.value.toFloat(),
        )

        fun fromDomain(domainObj: ShippableOrderPlaced) = ShippableOrderPlacedDto(
            orderId = domainObj.orderId.value,
            shippingAddress = AddressDto.fromAddress(domainObj.shippingAddress),
            shipmentLines = domainObj.shipmentLines.map(::fromShippableOrderLine),
            pdf = domainObj.pdf,
        )
    }
}

//===============================================
// DTO for BillableOrderPlaced event
//===============================================

data class BillableOrderPlacedDto(
    val orderId: String,
    val billingAddress: AddressDto,
    val amountToBill: Float,
) {
    companion object {
        fun fromDomain(domainObj:BillableOrderPlaced) = BillableOrderPlacedDto(
            orderId = domainObj.orderId.value,
            billingAddress = AddressDto.fromAddress(domainObj.billingAddress),
            amountToBill = domainObj.amountToBill.value,
        )
    }
}


//===============================================
// DTO for OrderAcknowledgmentSent event
//===============================================

/// Event to send to other bounded contexts
data class OrderAcknowledgmentSentDto(
    val orderId: String,
    val emailAddress: String,
) {
    companion object {
        fun fromDomain(domainObj: OrderAcknowledgementSent) = OrderAcknowledgmentSentDto(
            orderId = domainObj.orderId.value,
            emailAddress = domainObj.emailAddress.value,
        )
    }
}

//===============================================
// DTO for PlaceOrderEvent
//===============================================

interface PlaceOrderEventDto: Map<String, Any> {
    companion object {

        fun fromDomain(domainObj: PlaceOrderEvent): Map<String, Any> = when(domainObj) {
            is ShippableOrderPlacedEvent -> {
                val obj = ShippableOrderPlacedDto.fromDomain(domainObj.shippableOrderPlaced)
                val key = "ShippableOrderPlaced"
                mapOf(key to obj)
            }
            is BillableOrderPlacedEvent -> {
                val obj = BillableOrderPlacedDto.fromDomain(domainObj.billableOrderPlaced)
                val key = "BillableOrderPlaced"
                mapOf(key to obj)
            }
            is AcknowledgmentSentEvent -> {
                val obj = OrderAcknowledgmentSentDto.fromDomain(domainObj.orderAcknowledgmentSent)
                val key = "OrderAcknowledgmentSent"
                mapOf(key to obj)
            }
        }
    }
}

//===============================================
// DTO for PlaceOrderError
//===============================================

data class PlaceOrderErrorDto(
    val code : String,
    val message : String,
) {
    companion object {

        fun fromDomain(domainObj: PlaceOrderError) : PlaceOrderErrorDto = when(domainObj) {
            is Validation -> {
                PlaceOrderErrorDto("ValidationError", domainObj.validationError.value)
            }
            is Pricing -> {
                PlaceOrderErrorDto("PricingError", domainObj.pricingError.value)
            }
            is RemoteService -> {
                PlaceOrderErrorDto("RemoteServiceError", domainObj.remoteServiceError.exception.message ?: "") // TODO: Fallback to empty string is not exactly like in the original
            }
        }
    }
}
