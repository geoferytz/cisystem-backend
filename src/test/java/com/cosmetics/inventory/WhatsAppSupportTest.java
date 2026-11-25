package com.cosmetics.inventory;

import com.cosmetics.inventory.whatsapp.WhatsAppMessageEntity;
import com.cosmetics.inventory.whatsapp.WhatsAppMessageEntity.Status;
import com.cosmetics.inventory.whatsapp.WhatsAppSupport;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class WhatsAppSupportTest {

	@Test
	void normalizesTanzanianAndInternationalNumbers() {
		assertEquals("255712345678", WhatsAppSupport.normalizePhone("0712 345 678"));
		assertEquals("255712345678", WhatsAppSupport.normalizePhone("712345678"));
		assertEquals("255712345678", WhatsAppSupport.normalizePhone("+255 712-345-678"));
		assertEquals("255712345678", WhatsAppSupport.normalizePhone("00255712345678"));
		assertEquals("254712345678", WhatsAppSupport.normalizePhone("+254712345678"));
		assertNull(WhatsAppSupport.normalizePhone("12345"));
		assertNull(WhatsAppSupport.normalizePhone(null));
	}

	@Test
	void templateParamsAreSingleLineAndCapped() {
		assertEquals("a b", WhatsAppSupport.templateParam("a\n\tb", 50));
		assertEquals("-", WhatsAppSupport.templateParam("  ", 50));
		assertEquals("abcd…", WhatsAppSupport.templateParam("abcdefgh", 5));
		assertFalse(WhatsAppSupport.templateParam("a      b", 50).contains("    "));
	}

	@Test
	void validatesMetaSignature() throws Exception {
		byte[] body = "{\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		String header = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));

		assertTrue(WhatsAppSupport.validSignature("secret", body, header));
		assertFalse(WhatsAppSupport.validSignature("other", body, header));
		assertFalse(WhatsAppSupport.validSignature("secret", body, null));
		assertFalse(WhatsAppSupport.validSignature("", body, header));
	}

	@Test
	void statusNeverMovesBackwardsExceptToFailed() {
		WhatsAppMessageEntity m = new WhatsAppMessageEntity();
		m.advanceTo(Status.READ);
		m.advanceTo(Status.DELIVERED);
		assertEquals(Status.READ, m.getStatus());
		m.advanceTo(Status.FAILED);
		assertEquals(Status.FAILED, m.getStatus());
	}
}
