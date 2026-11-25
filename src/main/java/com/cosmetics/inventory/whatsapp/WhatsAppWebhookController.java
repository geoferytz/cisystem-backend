package com.cosmetics.inventory.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Meta webhook: GET is the one-time subscription handshake, POST delivers message status updates.
 * Public endpoint, so every POST is authenticated with the X-Hub-Signature-256 HMAC.
 */
@RestController
@RequestMapping("/webhooks/whatsapp")
public class WhatsAppWebhookController {
	private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

	private final WhatsAppProperties props;
	private final WhatsAppMessageRepository messages;
	private final ObjectMapper mapper;

	public WhatsAppWebhookController(WhatsAppProperties props, WhatsAppMessageRepository messages, ObjectMapper mapper) {
		this.props = props;
		this.messages = messages;
		this.mapper = mapper;
	}

	@GetMapping
	public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
										 @RequestParam(name = "hub.verify_token", required = false) String token,
										 @RequestParam(name = "hub.challenge", required = false) String challenge) {
		String expected = props.verifyToken();
		boolean ok = "subscribe".equals(mode) && expected != null && !expected.isBlank() && token != null
				&& MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
		return ok ? ResponseEntity.ok(challenge) : ResponseEntity.status(403).build();
	}

	@PostMapping
	@Transactional
	public ResponseEntity<Void> receive(@RequestBody byte[] body,
										@RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
		if (!WhatsAppSupport.validSignature(props.appSecret(), body, signature)) {
			return ResponseEntity.status(403).build();
		}
		try {
			for (JsonNode entry : mapper.readTree(body).path("entry")) {
				for (JsonNode change : entry.path("changes")) {
					for (JsonNode s : change.path("value").path("statuses")) {
						applyStatus(s);
					}
				}
			}
		} catch (Exception e) {
			log.warn("Could not process WhatsApp webhook: {}", e.getMessage());
		}
		// Always 200 once authenticated, otherwise Meta keeps retrying the same payload.
		return ResponseEntity.ok().build();
	}

	private void applyStatus(JsonNode s) {
		WhatsAppMessageEntity.Status next = switch (s.path("status").asText("")) {
			case "sent" -> WhatsAppMessageEntity.Status.SENT;
			case "delivered" -> WhatsAppMessageEntity.Status.DELIVERED;
			case "read" -> WhatsAppMessageEntity.Status.READ;
			case "failed" -> WhatsAppMessageEntity.Status.FAILED;
			default -> null;
		};
		if (next == null) return;
		messages.findFirstByWamid(s.path("id").asText("")).ifPresent(m -> {
			m.advanceTo(next);
			if (next == WhatsAppMessageEntity.Status.FAILED) {
				JsonNode err = s.path("errors").path(0);
				String detail = err.path("error_data").path("details").asText("");
				m.setError(detail.isBlank() ? err.path("title").asText("Delivery failed") : detail);
			}
		});
	}
}
