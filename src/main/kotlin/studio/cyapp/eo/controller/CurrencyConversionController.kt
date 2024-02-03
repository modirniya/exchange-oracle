package studio.cyapp.eo.controller

import org.springframework.web.bind.annotation.*
import studio.cyapp.eo.converter.CurrencyConverter
import java.math.BigDecimal

@RestController
@RequestMapping("/api/currency")
class CurrencyConversionController(private val currencyConverter: CurrencyConverter) {

    @GetMapping("/convert")
    fun convert(
        @RequestParam amount: BigDecimal,
        @RequestParam fromCurrency: String,
        @RequestParam toCurrency: String
    ): BigDecimal {
        return currencyConverter.convert(amount, fromCurrency, toCurrency)
    }
}
