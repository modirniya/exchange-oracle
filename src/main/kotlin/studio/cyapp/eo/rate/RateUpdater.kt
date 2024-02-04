package studio.cyapp.eo.rate

import jakarta.annotation.PostConstruct
import org.json.JSONObject
import org.slf4j.LoggerFactory
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

    private val logger = LoggerFactory.getLogger(RateUpdater::class.java)
    private val ratesFile = File("rates.json")
    private val webClient: WebClient = WebClient.create()

    @PostConstruct
    fun init() {
        logger.info("Initializing RateUpdater rev 1.b")
        if (ratesFile.exists()) {
            processExistingRatesFile()
        } else {
            logger.info("No rates file found, fetching new ones")
            updateRates()
        }
    }

    private fun processExistingRatesFile() {
        val content = ratesFile.readText()
        val lastUpdated = Instant.parse(JSONObject(content).getString("timestamp"))

        if (ChronoUnit.HOURS.between(lastUpdated, Instant.now()) < 6) {
            logger.info("Rates are less than 6 hours old, using them")
            currencyConverter.updateRates(content)
        } else {
            logger.info("Rates are older than 6 hours, fetching new ones")
            updateRates()
        }
    }

    fun updateRates() {
        fetchRates().subscribe { latestRatesStatus ->
            handleFetchedRates(latestRatesStatus)
        }
    }

    private fun handleFetchedRates(latestRatesStatus: WebsiteStatus) {
        if (latestRatesStatus.statusCode == 200) {
            processSuccessfulFetch(latestRatesStatus.body)
        } else {
            logger.error("Failed to fetch latest rates: ${latestRatesStatus.body}")
        }
    }

    private fun processSuccessfulFetch(responseBody: String?) {
        val jsonResp = JSONObject(responseBody)
        if (jsonResp.getBoolean("success")) {
            val jsonWithTimestamp = jsonResp.put("timestamp", Instant.now().toString()).toString()
            ratesFile.writeText(jsonWithTimestamp)
            currencyConverter.updateRates(jsonWithTimestamp)
        } else {
            jsonResp.getJSONObject("error").let {
                logger.error(
                    "API request failed.\ncode: {},type: {}\ninfo: {}\nExiting application.",
                    it.get("code"),
                    it.get("type"),
                    it.get("info")
                )
            }
            exitApplication()
        }
    }

    private fun fetchRates(): Mono<WebsiteStatus> {
        val accessKey = System.getenv("FIXER_ACCESS_KEY") ?: "none"
        logger.info("Fetching rates with access key: $accessKey")

        return webClient.get()
            .uri("http://data.fixer.io/api/latest?access_key={accessKey}", accessKey)
            .retrieve()
            .bodyToMono(String::class.java)
            .timeout(Duration.ofSeconds(6))
            .map { resp -> WebsiteStatus(200, resp) }
            .onErrorResume { e -> handleFetchError(e) }
    }

    private fun handleFetchError(e: Throwable): Mono<WebsiteStatus> {
        val status = if (e is WebClientResponseException) e.statusCode.value() else 0
        logger.error("Error occurred while fetching rates: ${e.message}")
        return Mono.just(WebsiteStatus(status, null))
    }

    private fun exitApplication() {
        logger.info("Exiting application")
        exitProcess(1)
    }
}
