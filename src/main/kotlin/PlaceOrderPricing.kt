package org.example

fun createPricingMethod(promotionCode: String?) = if (promotionCode.isNullOrBlank()) {
    Standard
} else {
    Promotion(PromotionCode(promotionCode))
}

fun getPricingFunction(standardPrices: GetStandardPrices, promoPrices: GetPromotionPrices): GetPricingFunction {
    return { pricingMethod: PricingMethod ->

        val getStandardPrice : GetProductPrice = standardPrices

        val getPromotionPrice = { promotionCode : PromotionCode ->
            { productCode:ProductCode ->
                promoPrices(promotionCode)(productCode) ?: getStandardPrice(productCode)
            }
        }

        when(pricingMethod) {
            Standard -> getStandardPrice
            is Promotion -> getPromotionPrice(pricingMethod.promotionCode)
        }
    }
}