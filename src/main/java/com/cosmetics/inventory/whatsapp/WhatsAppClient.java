package com.cosmetics.inventory.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Thin client for the Graph API /messages endpoint (template messages only). */
@Component
public class WhatsAppClient {
	private final WhatsAppProperties props;
	private final ObjectMapper mapper;
	private final RestClient rest;

	public WhatsAppClient(WhatsAppProperties props, ObjectMapper mapper) {
		this.props = props;
		this.mapper = mapper;
		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(10_000);
		factory.setReadTimeout(20_000);
		this.rest = RestClient.builder().baseUrl("https://graph.facebook.com").requestFactory(factory).build();
	}

	/** Sends an approved template with positional body parameters. Returns the WhatsApp message id (wamid). */
	public String sendTemplate(String to, String template, List<String> bodyParams) {
		if (!props.ready()) throw new WhatsAppException("WhatsApp API is not configured");
		var params = bodyParams.stream().map(p -> Map.of("type", "text", "text", p)).toList();
		var payload = Map.of(
				"messaging_product", "whatsapp",
				"to", to,
				"type", "template",
				"template", Map.of(
						"name", template,
						"language", Map.of("code", props.templateLanguage()),
						"components", List.of(Map.of("type", "body", "parameters", params))));
		JsonNode res = rest.post()
				.uri("/{version}/{phoneId}/messages", props.apiVersion(), props.phoneNumberId())
				.header("Authorization", "Bearer " + props.accessToken())
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload)
				.retrieve()
				.onStatus(HttpStatusCode::isError, (req, resp) -> {
					throw new WhatsAppException(errorMessage(new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8)));
				})
				.body(JsonNode.class);
		String id = res == null ? "" : res.path("messages").path(0).path("id").asText("");
		if (id.isBlank()) throw new WhatsAppException("WhatsApp did not return a message id");
		return id;
	}

	private String errorMessage(String body) {
		try {
			JsonNode err = mapper.readTree(body).path("error");
			String detail = err.path("error_data").path("details").asText("");
			String msg = err.path("message").asText("WhatsApp API error");
			return detail.isBlank() ? msg : msg + ": " + detail;
		} catch (Exception e) {
			return "WhatsApp API error";
		}
	}

	public static class WhatsAppException extends RuntimeException {
		public WhatsAppException(String message) {
			super(message);
		}
	}
}
