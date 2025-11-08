package chat.giga.springai.api.auth.bearer;

import static org.junit.jupiter.api.Assertions.*;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@Epic("GigaChat API")
@Feature("Authentication")
@DisplayName("GigaChatBearerToken Tests")
@Execution(ExecutionMode.CONCURRENT)
class GigaChatBearerTokenTest {

    private static final String VALID_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test";
    private static final long ONE_HOUR_MS = 3600_000L;

    @Nested
    @Story("Token Creation")
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @Issue("56")
        @Severity(SeverityLevel.BLOCKER)
        @DisplayName("Should create token with valid parameters")
        @Description("Verify that token is created successfully with valid access token and future expiration time")
        void shouldCreateTokenWithValidParameters() {
            // Given
            var currentTime = System.currentTimeMillis();
            var expiresAt = currentTime + ONE_HOUR_MS;

            // When
            var token = new GigaChatBearerToken(VALID_TOKEN, expiresAt);

            // Then
            assertNotNull(token, "Token should not be null");
            assertEquals(VALID_TOKEN, token.accessToken(), "Access token should match the provided value");
            assertEquals(expiresAt, token.expiresAt(), "Expiration time should match the provided value");
            assertTrue(token.refreshTime() > currentTime, "Refresh time should be in the future");
            assertTrue(token.refreshTime() < expiresAt, "Refresh time should be before expiration time");
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("Should calculate refresh time as 95% of token lifetime")
        @Description(
                "Verify that refresh time is calculated correctly as 95% of the time between creation and expiration")
        void shouldCalculateRefreshTimeCorrectly() {
            // Given
            var currentTime = System.currentTimeMillis();
            var expiresAt = currentTime + ONE_HOUR_MS;
            var expectedDelta = (long) (ONE_HOUR_MS * 0.95);
            var expectedRefreshTime = currentTime + expectedDelta;

            // When
            var token = new GigaChatBearerToken(VALID_TOKEN, expiresAt);

            // Then
            var actualRefreshTime = token.refreshTime();
            var tolerance = 100L; // 100ms tolerance for timing differences

            assertTrue(
                    Math.abs(actualRefreshTime - expectedRefreshTime) <= tolerance,
                    String.format(
                            "Refresh time should be approximately 95%% of lifetime. "
                                    + "Expected: %d, Actual: %d, Difference: %d ms",
                            expectedRefreshTime, actualRefreshTime, Math.abs(actualRefreshTime - expectedRefreshTime)));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @Issue("56")
        @Severity(SeverityLevel.BLOCKER)
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Should throw exception when access token is null, empty or blank")
        @Description("Verify that IllegalArgumentException is thrown for invalid access token values")
        void shouldThrowExceptionForInvalidAccessToken(String invalidToken) {
            // Given
            var expiresAt = System.currentTimeMillis() + ONE_HOUR_MS;

            // When & Then
            var exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new GigaChatBearerToken(invalidToken, expiresAt),
                    "Should throw IllegalArgumentException for invalid access token");

            assertTrue(
                    exception.getMessage().contains("Access token cannot be null or blank"),
                    "Exception message should indicate that access token is invalid");
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("Should throw exception when expiration time is in the past")
        @Description(
                "Verify that IllegalArgumentException is thrown when expiresAt is less than or equal to current time")
        void shouldThrowExceptionForPastExpirationTime() {
            // Given
            var pastTime = System.currentTimeMillis() - ONE_HOUR_MS;

            // When & Then
            var exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new GigaChatBearerToken(VALID_TOKEN, pastTime),
                    "Should throw IllegalArgumentException for past expiration time");

            assertTrue(
                    exception.getMessage().contains("must be in the future"),
                    "Exception message should indicate that expiration time must be in the future");
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("Should throw exception when expiration time equals current time")
        @Description("Verify that token cannot be created with expiration time equal to current time")
        void shouldThrowExceptionForCurrentTimeAsExpiration() {
            // Given
            var currentTime = System.currentTimeMillis();

            // When & Then
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new GigaChatBearerToken(VALID_TOKEN, currentTime),
                    "Should throw IllegalArgumentException when expiration time equals current time");
        }
    }

    @Nested
    @Story("Token Refresh Logic")
    @DisplayName("Refresh Time Tests")
    class RefreshTimeTests {

        @Test
        @Issue("56")
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("Should not need refresh for newly created token")
        @Description("Verify that a newly created token does not need immediate refresh")
        void shouldNotNeedRefreshForNewToken() {
            // Given
            var expiresAt = System.currentTimeMillis() + ONE_HOUR_MS;
            var token = new GigaChatBearerToken(VALID_TOKEN, expiresAt);

            // When
            var needsRefresh = token.needsRefresh();

            // Then
            assertFalse(needsRefresh, "Newly created token should not need refresh immediately");
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("Should need refresh when refresh time is reached")
        @Description("Verify that token needs refresh after its refresh time has passed")
        void shouldNeedRefreshAfterRefreshTime() throws InterruptedException {
            // Given
            var shortLifetime = 200L; // 200ms lifetime
            var expiresAt = System.currentTimeMillis() + shortLifetime;
            var token = new GigaChatBearerToken(VALID_TOKEN, expiresAt);

            // When - wait for 95% of lifetime + small buffer
            Thread.sleep((long) (shortLifetime * 0.95) + 50);
            var needsRefresh = token.needsRefresh();

            // Then
            assertTrue(needsRefresh, "Token should need refresh after refresh time has passed");
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("Should not need refresh before refresh time")
        @Description("Verify that token does not need refresh before its refresh time")
        void shouldNotNeedRefreshBeforeRefreshTime() throws InterruptedException {
            // Given
            var lifetime = 10_000L; // 10 seconds lifetime instead of 1 minute
            var expiresAt = System.currentTimeMillis() + lifetime;
            var token = new GigaChatBearerToken(VALID_TOKEN, expiresAt);

            // When - wait for half of 95% lifetime (47.5% of total lifetime)
            var waitTime = (long) (lifetime * 0.95 * 0.5);
            Thread.sleep(waitTime);
            var needsRefresh = token.needsRefresh();

            // Then
            assertFalse(
                    needsRefresh,
                    String.format(
                            "Token should not need refresh before refresh time is reached. "
                                    + "Waited %d ms (%.1f%% of lifetime), refresh time is at %.1f%% of lifetime",
                            waitTime, (waitTime * 100.0 / lifetime), 95.0));
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("Should be thread-safe when checking refresh status")
        @Description("Verify that needsRefresh() method is thread-safe and can be called concurrently")
        void shouldBeThreadSafeForRefreshCheck() throws InterruptedException {
            // Given
            var expiresAt = System.currentTimeMillis() + ONE_HOUR_MS;
            var token = new GigaChatBearerToken(VALID_TOKEN, expiresAt);
            var threadCount = 100;
            var startLatch = new CountDownLatch(1);
            var doneLatch = new CountDownLatch(threadCount);
            var successCount = new AtomicInteger(0);

            // When
            for (var i = 0; i < threadCount; i++) {
                new Thread(() -> {
                            try {
                                startLatch.await();
                                var needsRefresh = token.needsRefresh();
                                if (!needsRefresh) {
                                    successCount.incrementAndGet();
                                }
                            } catch (Exception e) {
                                fail("Should not throw exception during concurrent access: " + e.getMessage());
                            } finally {
                                doneLatch.countDown();
                            }
                        })
                        .start();
            }

            startLatch.countDown();
            var completed = doneLatch.await(5, TimeUnit.SECONDS);

            // Then
            assertTrue(completed, "All threads should complete within timeout");
            assertEquals(
                    threadCount,
                    successCount.get(),
                    "All threads should successfully check refresh status without errors");
        }
    }

    @Nested
    @Story("Token Expiration")
    @DisplayName("Expiration Tests")
    class ExpirationTests {

        @Test
        @Issue("56")
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("Should not be expired for newly created token")
        @Description("Verify that a newly created token is not expired immediately")
        void shouldNotBeExpiredForNewToken() {
            // Given
            var expiresAt = System.currentTimeMillis() + ONE_HOUR_MS;
            var token = new GigaChatBearerToken(VALID_TOKEN, expiresAt);

            // When
            var isExpired = token.isExpired();

            // Then
            assertFalse(isExpired, "Newly created token should not be expired");
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("Should be expired after expiration time")
        @Description("Verify that token is marked as expired after its expiration time has passed")
        void shouldBeExpiredAfterExpirationTime() throws InterruptedException {
            // Given
            var shortLifetime = 100L;
            var expiresAt = System.currentTimeMillis() + shortLifetime;
            var token = new GigaChatBearerToken(VALID_TOKEN, expiresAt);

            // When
            Thread.sleep(shortLifetime + 50);
            var isExpired = token.isExpired();

            // Then
            assertTrue(isExpired, "Token should be expired after expiration time has passed");
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("Should need refresh before becoming expired")
        @Description("Verify that token needs refresh before it actually expires (at 95% of lifetime)")
        void shouldNeedRefreshBeforeExpiration() throws InterruptedException {
            // Given
            var lifetime = 500L;
            var expiresAt = System.currentTimeMillis() + lifetime;
            var token = new GigaChatBearerToken(VALID_TOKEN, expiresAt);

            // When - wait for 96% of lifetime (past refresh time, before expiration)
            Thread.sleep((long) (lifetime * 0.96));

            // Then
            assertTrue(token.needsRefresh(), "Token should need refresh at 96% of lifetime");
            assertFalse(token.isExpired(), "Token should not be expired yet at 96% of lifetime");
        }
    }

    @Nested
    @Story("Token Immutability")
    @DisplayName("Immutability Tests")
    class ImmutabilityTests {

        @Test
        @Issue("56")
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("Should be immutable - getters return same values")
        @Description("Verify that all getters consistently return the same values")
        void shouldBeImmutable() {
            // Given
            var expiresAt = System.currentTimeMillis() + ONE_HOUR_MS;
            var token = new GigaChatBearerToken(VALID_TOKEN, expiresAt);

            // When
            var accessToken1 = token.accessToken();
            var accessToken2 = token.accessToken();
            var expiresAt1 = token.expiresAt();
            var expiresAt2 = token.expiresAt();
            var refreshTime1 = token.refreshTime();
            var refreshTime2 = token.refreshTime();

            // Then
            assertSame(accessToken1, accessToken2, "Access token getter should return same reference");
            assertEquals(expiresAt1, expiresAt2, "Expiration time getter should return same value");
            assertEquals(refreshTime1, refreshTime2, "Refresh time getter should return same value");
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.CRITICAL)
        @DisplayName("Should be thread-safe - concurrent reads return consistent values")
        @Description("Verify that multiple threads can safely read token properties concurrently")
        void shouldBeThreadSafeForReads() throws InterruptedException {
            // Given
            var expiresAt = System.currentTimeMillis() + ONE_HOUR_MS;
            var token = new GigaChatBearerToken(VALID_TOKEN, expiresAt);
            var threadCount = 50;
            var startLatch = new CountDownLatch(1);
            var doneLatch = new CountDownLatch(threadCount);
            var successCount = new AtomicInteger(0);

            // When
            for (var i = 0; i < threadCount; i++) {
                new Thread(() -> {
                            try {
                                startLatch.await();

                                var accessToken = token.accessToken();
                                var expires = token.expiresAt();
                                var refresh = token.refreshTime();

                                if (VALID_TOKEN.equals(accessToken) && expires == expiresAt && refresh > 0) {
                                    successCount.incrementAndGet();
                                }
                            } catch (Exception e) {
                                fail("Should not throw exception during concurrent reads: " + e.getMessage());
                            } finally {
                                doneLatch.countDown();
                            }
                        })
                        .start();
            }

            startLatch.countDown();
            var completed = doneLatch.await(5, TimeUnit.SECONDS);

            // Then
            assertTrue(completed, "All threads should complete within timeout");
            assertEquals(threadCount, successCount.get(), "All threads should successfully read consistent values");
        }
    }

    @Nested
    @Story("Value Object Behavior")
    @DisplayName("Equals and HashCode Tests")
    class EqualsAndHashCodeTests {

        @Test
        @Issue("56")
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("Should be equal when all fields are equal")
        @Description("Verify that two tokens with same values are considered equal")
        void shouldBeEqualWhenFieldsAreEqual() {
            // Given
            var expiresAt = System.currentTimeMillis() + ONE_HOUR_MS;
            var token1 = new GigaChatBearerToken(VALID_TOKEN, expiresAt);

            // Create using canonical constructor with exact same values
            var token2 = new GigaChatBearerToken(VALID_TOKEN, token1.expiresAt(), token1.refreshTime());

            // When & Then
            assertEquals(token1, token2, "Tokens with same field values should be equal");
            assertEquals(
                    token1.hashCode(), token2.hashCode(), "Tokens with same field values should have same hash code");
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("Should not be equal when access tokens differ")
        @Description("Verify that tokens with different access tokens are not equal")
        void shouldNotBeEqualWhenAccessTokensDiffer() {
            // Given
            var expiresAt = System.currentTimeMillis() + ONE_HOUR_MS;
            var token1 = new GigaChatBearerToken(VALID_TOKEN, expiresAt);
            var token2 = new GigaChatBearerToken("different_token", expiresAt);

            // When & Then
            assertNotEquals(token1, token2, "Tokens with different access tokens should not be equal");
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.TRIVIAL)
        @DisplayName("Should not be equal to null")
        @Description("Verify that token is not equal to null")
        void shouldNotBeEqualToNull() {
            // Given
            var expiresAt = System.currentTimeMillis() + ONE_HOUR_MS;
            var token = new GigaChatBearerToken(VALID_TOKEN, expiresAt);

            // When & Then
            assertNotEquals(null, token, "Token should not be equal to null");
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.TRIVIAL)
        @SuppressWarnings("EqualsWithItself")
        @DisplayName("Should be equal to itself")
        @Description("Verify reflexive property of equals")
        void shouldBeEqualToItself() {
            // Given
            var expiresAt = System.currentTimeMillis() + ONE_HOUR_MS;
            var token = new GigaChatBearerToken(VALID_TOKEN, expiresAt);

            // When & Then
            assertEquals(token, token, "Token should be equal to itself (reflexive property)");
        }
    }

    @Nested
    @Story("String Representation")
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @Issue("56")
        @Severity(SeverityLevel.NORMAL)
        @DisplayName("Should mask access token in toString output")
        @Description("Verify that toString masks the access token for security, showing only last 4 characters")
        void shouldMaskAccessTokenInToString() {
            // Given
            var longToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.test1234";
            var expiresAt = System.currentTimeMillis() + ONE_HOUR_MS;
            var token = new GigaChatBearerToken(longToken, expiresAt);

            // When
            var toString = token.toString();

            // Then
            assertFalse(toString.contains(longToken), "toString should not contain full access token");
            assertTrue(toString.contains("***"), "toString should contain masking characters");
            assertTrue(toString.contains("1234"), "toString should contain last 4 characters of token");
            assertTrue(toString.contains("GigaChatBearerToken"), "toString should contain class name");
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.TRIVIAL)
        @DisplayName("Should include all relevant fields in toString")
        @Description("Verify that toString contains information about all important fields")
        void shouldIncludeAllFieldsInToString() {
            // Given
            var expiresAt = System.currentTimeMillis() + ONE_HOUR_MS;
            var token = new GigaChatBearerToken(VALID_TOKEN, expiresAt);

            // When
            var toString = token.toString();

            // Then
            assertTrue(toString.contains("accessToken="), "toString should contain accessToken field");
            assertTrue(toString.contains("expiresAt="), "toString should contain expiresAt field");
            assertTrue(toString.contains("refreshTime="), "toString should contain refreshTime field");
            assertTrue(toString.contains("needsRefresh="), "toString should contain needsRefresh status");
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.MINOR)
        @DisplayName("Should handle short tokens in toString")
        @Description("Verify that toString handles tokens shorter than 4 characters")
        void shouldHandleShortTokensInToString() {
            // Given
            var shortToken = "abc";
            var expiresAt = System.currentTimeMillis() + ONE_HOUR_MS;
            var token = new GigaChatBearerToken(shortToken, expiresAt);

            // When
            var toString = token.toString();

            // Then
            assertFalse(toString.contains(shortToken), "toString should not contain full short token");
            assertTrue(toString.contains("***"), "toString should contain masking characters for short token");
        }
    }

    @Nested
    @Story("Edge Cases")
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @Issue("56")
        @Severity(SeverityLevel.MINOR)
        @DisplayName("Should handle token with 1 second lifetime")
        @Description("Verify that token works correctly with very short lifetime")
        void shouldHandleVeryShortLifetime() {
            // Given
            var expiresAt = System.currentTimeMillis() + 1000L;

            // When
            var token = new GigaChatBearerToken(VALID_TOKEN, expiresAt);

            // Then
            assertNotNull(token, "Token should be created even with 1 second lifetime");
            assertTrue(
                    token.refreshTime() < expiresAt, "Refresh time should be calculated correctly for short lifetime");
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.MINOR)
        @DisplayName("Should handle token with very long lifetime")
        @Description("Verify that token works correctly with lifetime of 365 days")
        void shouldHandleVeryLongLifetime() {
            // Given
            var oneYearMs = 365L * 24 * 60 * 60 * 1000;
            var expiresAt = System.currentTimeMillis() + oneYearMs;

            // When
            var token = new GigaChatBearerToken(VALID_TOKEN, expiresAt);

            // Then
            assertNotNull(token, "Token should be created with very long lifetime");
            assertFalse(token.needsRefresh(), "Token should not need refresh immediately even with long lifetime");
            assertFalse(token.isExpired(), "Token should not be expired with long lifetime");
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.MINOR)
        @DisplayName("Should handle token with maximum long value expiration")
        @Description("Verify that token can handle Long.MAX_VALUE as expiration time")
        void shouldHandleMaxLongExpiration() {
            // Given
            var maxExpiration = Long.MAX_VALUE;

            // When
            var token = new GigaChatBearerToken(VALID_TOKEN, maxExpiration);

            // Then
            assertNotNull(token, "Token should be created with maximum expiration time");
            assertEquals(maxExpiration, token.expiresAt(), "Expiration time should be Long.MAX_VALUE");
            assertFalse(token.needsRefresh(), "Token with max expiration should not need refresh");
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.MINOR)
        @DisplayName("Should handle very long access token")
        @Description("Verify that token can handle access tokens longer than 1000 characters")
        void shouldHandleVeryLongAccessToken() {
            // Given
            var longToken = "A".repeat(10000);
            var expiresAt = System.currentTimeMillis() + ONE_HOUR_MS;

            // When
            var token = new GigaChatBearerToken(longToken, expiresAt);

            // Then
            assertNotNull(token, "Token should be created with very long access token");
            assertEquals(longToken, token.accessToken(), "Long access token should be stored correctly");
            assertEquals(10000, token.accessToken().length(), "Access token length should be preserved");
        }

        @Test
        @Issue("56")
        @Severity(SeverityLevel.MINOR)
        @DisplayName("Should handle special characters in access token")
        @Description("Verify that token can handle access tokens with special characters")
        void shouldHandleSpecialCharactersInToken() {
            // Given
            var specialToken = "token-with_special.chars+symbols/equals=end";
            var expiresAt = System.currentTimeMillis() + ONE_HOUR_MS;

            // When
            var token = new GigaChatBearerToken(specialToken, expiresAt);

            // Then
            assertNotNull(token, "Token should be created with special characters");
            assertEquals(specialToken, token.accessToken(), "Special characters in token should be preserved");
        }
    }
}
