package studio.cyapp.eo.rate

import jakarta.annotation.PostConstruct
import org.json.JSONObject
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import studio.cyapp.eo.converter.CurrencyConverter
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.system.exitProcess

data class WebsiteStatus(
    val statusCode: Int,
    val body: String?
)

@Component
class RateUpdater(
    private val currencyConverter: CurrencyConverter
) {

    private val ratesFile = File("rates.json")
    private val webClient: WebClient = WebClient.create()

    @PostConstruct
    fun init() {
        if (ratesFile.exists()) {
            val content = ratesFile.readText()
            val lastUpdated = Instant.parse(JSONObject(content).getString("timestamp"))

            if (ChronoUnit.HOURS.between(lastUpdated, Instant.now()) < 6) {
                println("Rates are less than 6 hours old, using them")
                currencyConverter.updateRates(content)
            } else {
                println("Rates are older than 6 hours, fetching new ones")
                updateRates()
            }
        } else {
            println("No rates file found, fetching new ones")
            updateRates()
        }
    }

    fun updateRates() {
        fetchRates().subscribe { latestRatesStatus ->
            if (latestRatesStatus.statusCode == 200) {
                val jsonResponse = JSONObject(latestRatesStatus.body)
                if (!jsonResponse.getBoolean("success")) {
                    println("API request failed. Exiting application.")
                    //exitApplication()
                }
                val jsonWithTimestamp = jsonResponse
                    .put("timestamp", Instant.now().toString())
                    .toString()
                ratesFile.writeText(jsonWithTimestamp)
                currencyConverter.updateRates(jsonWithTimestamp)
            } else {
                println("Failed to fetch latest rates: ${latestRatesStatus.body}")
            }
        }
    }

    private fun fetchRates(): Mono<WebsiteStatus> {
        val accessKey = System.getenv("FIXER_ACCESS_KEY") ?: "none"

        return webClient.get()
            .uri("http://data.fixer.io/api/latest?access_key={accessKey}", accessKey)
            .retrieve()
            .bodyToMono(String::class.java)
            .timeout(Duration.ofSeconds(6))
            .map { resp -> WebsiteStatus(200, resp) }
            .onErrorResume { e ->
                val status = if (e is WebClientResponseException) e.statusCode.value() else 0
                Mono.just(WebsiteStatus(status, null))
            }
    }


    private fun exitApplication() {
        exitProcess(1)
    }
}