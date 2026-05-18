package cash.atto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

inline fun <reified T : Any> WebTestClient.postForObject(
    uri: String,
    body: Any?,
): T =
    post()
        .uri(uri)
        .withBody(body)
        .exchange()
        .expectStatus()
        .is2xxSuccessful
        .expectBody(T::class.java)
        .returnResult()
        .responseBody ?: error("Response body was empty for POST $uri")

fun WebTestClient.postForLocation(
    uri: String,
    body: Any?,
) {
    post()
        .uri(uri)
        .withBody(body)
        .exchange()
        .expectStatus()
        .is2xxSuccessful
}

fun WebTestClient.postForJsonArray(
    uri: String,
    body: Any?,
): JsonArray {
    val response =
        post()
            .uri(uri)
            .withBody(body)
            .exchange()
            .expectStatus()
            .is2xxSuccessful
            .expectBody(String::class.java)
            .returnResult()
            .responseBody ?: "[]"

    return Json.parseToJsonElement(response).jsonArray
}

inline fun <reified T : Any> WebTestClient.getForObject(uri: String): T =
    get()
        .uri(uri)
        .exchange()
        .expectStatus()
        .is2xxSuccessful
        .expectBody(T::class.java)
        .returnResult()
        .responseBody ?: error("Response body was empty for GET $uri")

fun WebTestClient.put(
    uri: String,
    body: Any?,
) {
    put()
        .uri(uri)
        .withBody(body)
        .exchange()
        .expectStatus()
        .is2xxSuccessful
}

fun WebTestClient.RequestBodySpec.withBody(body: Any?): WebTestClient.RequestHeadersSpec<*> =
    if (body == null) {
        this
    } else {
        contentType(MediaType.APPLICATION_JSON).bodyValue(body)
    }
