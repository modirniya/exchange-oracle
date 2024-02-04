package studio.cyapp.eo.converter

import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service
class CurrencyConverter {
    private val lock = ReentrantReadWriteLock()
    private var base: String = ""
    private var rates: Map<String, BigDecimal> = emptyMap()

    companion object {
        private val logger = LoggerFactory.getLogger(CurrencyConverter::class.java)
    }

    fun updateRates(jsonData: String) {
        logger.info("Updating rates with new JSON data")
        lock.write {
            try {
                parseRates(jsonData)
                logger.info("Rates updated successfully. Base currency: $base")
            } catch (e: Exception) {
                logger.error("Error updating rates: ${e.message}")
                throw e
            }
        }
    }

    private fun parseRates(jsonData: String) {
        val jsonObject = JSONObject(jsonData)
        base = jsonObject.getString("base")
        rates = jsonObject.getJSONObject("rates").toMap().mapValues {
            BigDecimal(it.value.toString())
        }
    }

    fun convert(amount: BigDecimal, fromCurrency: String, toCurrency: String): BigDecimal {
        logger.info("Converting amount: {} from {} to {}", amount, fromCurrency, toCurrency)

        if (fromCurrency == toCurrency) {
            logger.info("From and to currencies are the same. No conversion needed.")
            return amount
        }

        return lock.read {
            try {
                val convertedAmount = calculateConversion(amount, fromCurrency, toCurrency)
                logger.info("Conversion result: $convertedAmount")
                convertedAmount
            } catch (e: IllegalArgumentException) {
                logger.error("Error in conversion: ${e.message}")
                throw e
            }
        }
    }

    private fun calculateConversion(amount: BigDecimal, fromCurrency: String, toCurrency: String): BigDecimal {
        val baseToFromRate = getRate(fromCurrency)
        val baseToToRate = getRate(toCurrency)

        val amountInBase = amount.divide(baseToFromRate, MathContext.DECIMAL64)
        return amountInBase.multiply(baseToToRate)
    }

    private fun getRate(currency: String): BigDecimal {
        return if (currency == base) BigDecimal.ONE else rates[currency]
            ?: throw IllegalArgumentException("Unknown currency: $currency")
    }
}
