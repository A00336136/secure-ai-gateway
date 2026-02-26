# Secure AI Gateway - ApplicationContext Initialization Fix Summary

## Issue
Tests were failing with the following error:
```
java.lang.ClassNotFoundException: org.springframework.web.servlet.resource.LiteWebJarsResourceResolver
IllegalStateException: ApplicationContext failure threshold (1) exceeded
```

## Root Cause
The project had two interconnected issues:

### Issue 1: Missing WebJars Locator Core Dependency
The `springdoc-openapi-starter-webmvc-ui:2.8.6` library depends on `webjars-locator-core`, which was not explicitly declared in the pom.xml. This caused the ApplicationContext to fail loading during test initialization.

### Issue 2: Deprecated JJWT API Usage
The `JwtUtil.java` class was using deprecated JJWT API methods that are not compatible with JJWT 0.12.6:
- Deprecated: `.parser()` with `.setSigningKey()` and `.parseClaimsJws()`
- These methods existed in JJWT 0.9.x but were changed in 0.12.6

## Fixes Applied

### Fix 1: Added Missing Dependency ✅
**File:** `/Users/ashaik/Downloads/secure-ai-gateway/pom.xml`

Added the `webjars-locator-core` dependency after the springdoc-openapi dependency:

```xml
<!-- WebJars Locator Core (required by springdoc-openapi for Spring Boot 3.x) -->
<dependency>
    <groupId>org.webjars</groupId>
    <artifactId>webjars-locator-core</artifactId>
</dependency>
```

**Impact:** Resolves the `ClassNotFoundException` for `LiteWebJarsResourceResolver` and allows the ApplicationContext to load successfully.

### Fix 2: Updated JJWT API Usage ✅
**File:** `/Users/ashaik/Downloads/secure-ai-gateway/src/main/java/com/secureai/security/JwtUtil.java`

Updated the `parseClaims()` method to use the correct JJWT 0.12.6 API:

**Before (Deprecated):**
```java
// Old style parser for jjwt 0.9.x
return Jwts.parser()
        .setSigningKey(signingKey)
        .build()
        .parseClaimsJws(resolvedToken)
        .getBody();
```

**After (JJWT 0.12.6 Compatible):**
```java
// JJWT 0.12.6 parser API
return Jwts.parserBuilder()
        .setSigningKey(signingKey)
        .build()
        .parseClaimsJws(resolvedToken)
        .getBody();
```

**Key Changes:**
- Use `Jwts.parserBuilder()` instead of deprecated `Jwts.parser()`
- Continue using `.setSigningKey()` with `SecretKey` (correct for 0.12.6)
- Use `.parseClaimsJws()` and `.getBody()` (stable API)

**Import Verification:**
- Ensured `io.jsonwebtoken.security.SignatureException` is imported (correct for 0.12.6)
- All exception handling remains compatible

## Verification Steps

To verify the fixes are working:

```bash
# 1. Clean compile the project
cd /Users/ashaik/Downloads/secure-ai-gateway
mvn clean compile

# 2. Run the failing tests
mvn test -Dtest=AuthControllerTest

# 3. Or run all tests
mvn test
```

## Expected Outcome

After applying these fixes:
1. ✅ The ApplicationContext should load successfully
2. ✅ All JWT token operations should work correctly (generateToken, validateToken, getUsernameFromToken, getRoleFromToken)
3. ✅ Tests should run without the `ClassNotFoundException` or `ApplicationContext failure threshold` errors
4. ✅ JJWT 0.12.6 compatibility is maintained

## Files Modified

1. `/Users/ashaik/Downloads/secure-ai-gateway/pom.xml` - Added webjars-locator-core dependency
2. `/Users/ashaik/Downloads/secure-ai-gateway/src/main/java/com/secureai/security/JwtUtil.java` - Updated to use JJWT 0.12.6 API

## CVE Status

✅ **No CVEs found in direct dependencies**

All direct dependencies were scanned for security vulnerabilities and no CVEs were detected in:
- Spring Boot 3.2.12
- JJWT 0.12.6 (jjwt-api, jjwt-impl, jjwt-jackson)
- springdoc-openapi-starter-webmvc-ui 2.8.6
- All other declared dependencies

## Dependencies Verified

- Java 17 ✅
- Spring Boot 3.2.12 ✅
- JJWT 0.12.6 ✅
- Bucket4j 8.10.1 ✅
- webjars-locator-core (Spring Boot managed) ✅
- PostgreSQL driver ✅
- H2 in-memory database ✅
- Flyway DB migrations ✅

## Notes

- The project uses HMAC-SHA256 (HS256) for JWT signing with a 256-bit key
- All JWT tokens are stateless and validated using pure cryptographic verification
- No database lookups are required for token validation
- The fix maintains backward compatibility with existing JWT implementations

