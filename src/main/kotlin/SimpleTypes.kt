package org.example

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold

data class String50 private constructor(val value: String) {
    companion object {
        fun create(fieldName: String, str: String): Result<String50, String> = ConstrainedType.createString(fieldName, ::String50, 50, str)
        fun createOption(fieldName: String, str: String?) = ConstrainedType.createStringOption(fieldName, ::String50, 50, str)
    }
}
enum class VipStatus {
    Normal, Vip;

    val value get() = when(this) {
        Normal -> "Normal"
        Vip -> "VIP"
    }
    companion object {
        fun create(fieldName: String, str: String) = when(str) {
            "normal", "Normal" -> Ok(Normal)
            "vip", "VIP" -> Ok(Vip)
            else -> Err("$fieldName: Must be one of 'Normal', 'VIP'")
        }
    }
}
data class EmailAddress private constructor(val value: String) {
    companion object {
        private val pattern = Regex.fromLiteral(".+@.+")
        fun create(fieldName: String, str: String): Result<EmailAddress, String> = ConstrainedType.createLike(fieldName, ::EmailAddress, pattern, str)
    }
}

class State {
}

data class ZipCode(val value: String) {
    companion object {
        private val pattern = Regex.fromLiteral("\\d{5}")
        fun create(fieldName: String, str: String?): Result<ZipCode, String> = ConstrainedType.createLike(fieldName, ::ZipCode, pattern, str)
    }
}
data class UsStateCode(val value: String) {
    companion object {
        private val pattern = Regex.fromLiteral("^(A[KLRZ]|C[AOT]|D[CE]|FL|GA|HI|I[ADLN]|K[SY]|LA|M[ADEINOST]|N[CDEHJMVY]|O[HKR]|P[AR]|RI|S[CD]|T[NX]|UT|V[AIT]|W[AIVY])$")
        fun create(fieldName: String, str: String?): Result<UsStateCode, String> = ConstrainedType.createLike(fieldName, ::UsStateCode, pattern, str)
    }
}
data class OrderId(val value: String) {
    companion object {
        fun create(fieldName: String, str: String?) = ConstrainedType.createString(fieldName, ::OrderId, 50, str)
    }
}
data class OrderLineId(val value: String) {
    companion object {
        fun create(fieldName: String, str: String?) = ConstrainedType.createString(fieldName, ::OrderLineId, 50, str)
    }
}
// TODO: Original code has an intermediate datatype, but I don't know why
sealed interface ProductCode {
    companion object {
        fun create(fieldName: String, code: String?): Result<ProductCode, String> {
            return if(code.isNullOrBlank()) {
                Err("$fieldName: Must not be null or empty")
            } else (if (code.startsWith("W")) {
                WidgetCode.create(fieldName, code)
            } else if(code.startsWith("G")) {
                GizmoCode.create(fieldName, code)
            } else {
                Err("$fieldName: Format not recognized '$code'")
            }) as Result<ProductCode, String>
        }
    }

}
data class WidgetCode(val value: String): ProductCode {
    companion object {
        private val pattern = Regex.fromLiteral("W\\d{4}")
        fun create(fieldName: String, code: String?) = ConstrainedType.createLike(fieldName, ::UsStateCode, pattern, code)
    }
}
data class GizmoCode(val value: String): ProductCode {
    companion object {
        private val pattern = Regex.fromLiteral("G\\d{3}")
        fun create(fieldName: String, code: String?) = ConstrainedType.createLike(fieldName, ::GizmoCode, pattern, code)
    }
}
sealed interface OrderQuantity {
    val value: Number

    companion object {
        fun create(fieldName: String, productCode: ProductCode, quantity: OrderQuantity): Result<OrderQuantity, String> = when(productCode) {
            is WidgetCode -> UnitQuantity.create(fieldName, quantity.value.toInt())
            is GizmoCode -> KilogramQuantity.create(fieldName, quantity.value.toFloat())
        }
    }

}
data class UnitQuantity(override val value: Int): OrderQuantity {
    companion object {
        fun create(fieldName: String, v: Int) = ConstrainedType.createInt(fieldName, ::UnitQuantity, 1, 1000, v)
    }
}
data class KilogramQuantity(override val value: Float): OrderQuantity {
    companion object {
        fun create(fieldName: String, v: Float) = ConstrainedType.createDecimal(fieldName, ::KilogramQuantity, 0.05f, 100f, v)
    }
}
data class Price(val value: Float) {
    fun multiply(p: Price): Result<Price, String> = create(value * p.value)
    companion object {
        fun create(v: Float) = ConstrainedType.createDecimal("Price", ::Price, 0.0f, 1000f, v)
        fun unsafeCreate(fieldName: String, v: Result<Float, String>) {
            return v.fold({ it -> it}, { throw IllegalStateException("Not expecting Price to be out of bounds: $it") })
        }
    }
}
data class BillingAmount(val value: Float) {
    companion object {
        fun create(v: Float) = ConstrainedType.createDecimal("BillingAmount", ::BillingAmount, 0.0f, 10000f, v)
        fun sumPrices(prices: List<Price>): Float = prices.map { it.value }.sum()
    }
}
data class PdfAttachment(val name: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PdfAttachment
        if (name != other.name) return false
        if (!bytes.contentEquals(other.bytes)) return false
        return true
    }

    override fun hashCode() = 31 * name.hashCode() + bytes.contentHashCode()
}
data class PromotionCode(val value: String)


object ConstrainedType {
    fun <T> createString(fieldName: String, ctor: (String) -> T, maxLength: Int, str: String?): Result<T, String> = if(str.isNullOrBlank()) {
        Err("$fieldName must not be null or empty")
    } else if(str.length > maxLength) {
        Err("$fieldName must not be more than $maxLength chars")
    } else {
        Ok(ctor(str))
    }

    fun <T> createStringOption(fieldName: String, ctor: (String) -> T, maxLength: Int, str: String?): Result<T?, String> = if(str.isNullOrBlank()) {
        Ok(null)
    } else if(str.length > maxLength) {
        Err("$fieldName must not be more than $maxLength chars")
    } else {
        Ok(ctor(str))
    }

    fun <T> createInt(fieldName: String, ctor: (Int) -> T, minVal: Int, maxVal: Int, i: Int): Result<T, String> = if(i < minVal) {
        Err("$fieldName must not be less than $minVal")
    } else if(i > maxVal) {
        Err("$fieldName must not be greater than $maxVal")
    } else {
        Ok(ctor(i))
    }

    fun <T> createDecimal(fieldName: String, ctor: (Float) -> T, minVal: Float, maxVal: Float, i: Float): Result<T, String> = if(i < minVal) {
        Err("$fieldName must not be less than $minVal")
    } else if(i > maxVal) {
        Err("$fieldName must not be greater than $maxVal")
    } else {
        Ok(ctor(i))
    }

    fun <T> createLike(fieldName: String, ctor: (String) -> T, pattern: Regex, str: String?): Result<T, String> = if(str.isNullOrBlank()) {
        Err("$fieldName must not be null or empty")
    } else if(pattern.matches(str)) {
        Ok(ctor(str))
    } else {
        Err("$fieldName: $str must match the pattern '$pattern'")
    }
}

class City
