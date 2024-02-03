package studio.cyapp.eo.converter

import org.json.JSONObject
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.util.concurrent.locks.ReentrantReadWriteLock

@Service
class CurrencyConverter {
    private val lock = ReentrantReadWriteLock()
    private var base: String = ""
    private var rates: Map<String, BigDecimal> = emptyMap()

    fun updateRates(jsonData: String) {
        lock.writeLock().lock()
        try {
            val jsonObject = JSONObject(jsonData)
            base = jsonObject.getString("base")
            rates = jsonObject.getJSONObject("rates").toMap().mapValues { BigDecimal(it.value.toString()) }
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun convert(amount: BigDecimal, fromCurrency: String, toCurrency: String): BigDecimal {
        if (fromCurrency == toCurrency) {
            return amount
        }

        val baseToFromRate = if (fromCurrency == base) BigDecimal.ONE else rates[fromCurrency]
            ?: throw IllegalArgumentException("Unknown 'from' currency")
        val baseToToRate = if (toCurrency == base) BigDecimal.ONE else rates[toCurrency]
            ?: throw IllegalArgumentException("Unknown 'to' currency")

        val amountInBase = amount.divide(baseToFromRate, MathContext.DECIMAL64)
        return amountInBase.multiply(baseToToRate)

    }

}
