package com.cosmetics.inventory.whatsapp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Stateless helpers shared by the sender and the webhook. */
public final class WhatsAppSupport {
	private WhatsAppSupport() {
	}

	/**
	 * Normalizes a phone number to WhatsApp's international digits-only format.
	 * Tanzanian local numbers (0712..., 712...) get the 255 prefix. Returns null if invalid.
	 */
	public static String normalizePhone(String raw) {
		if (raw == null) return null;
		String d = raw.replaceAll("\\D", "");
		if (d.startsWith("00")) d = d.substring(2);
		if (d.length() == 10 && d.startsWith("0")) d = "255" + d.substring(1);
		else if (d.length() == 9 && (d.startsWith("6") || d.startsWith("7"))) d = "255" + d;
		return d.length() >= 10 && d.length() <= 15 ? d : null;
	}

	/**
	 * Template parameters may not contain newlines, tabs or more than 4 consecutive spaces,
	 * and are capped in length by Meta.
	 */
	public static String templateParam(String value, int maxLength) {
		String v = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {4,}", "   ").trim();
		if (v.isEmpty()) return "-";
		return v.length() <= maxLength ? v : v.substring(0, maxLength - 1) + "…";
	}

	/** Verifies Meta's X-Hub-Signature-256 header (HMAC-SHA256 of the raw body with the app secret). */
	public static boolean validSignature(String appSecret, byte[] body, String header) {
		if (appSecret == null || appSecret.isBlank() || header == null || !header.startsWith("sha256=")) return false;
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] expected = HexFormat.of().formatHex(mac.doFinal(body)).getBytes(StandardCharsets.US_ASCII);
			byte[] given = header.substring("sha256=".length()).toLowerCase().getBytes(StandardCharsets.US_ASCII);
			return MessageDigest.isEqual(expected, given);
		} catch (Exception e) {
			return false;
		}
	}
}
