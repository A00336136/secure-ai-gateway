package secureaigateway

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class SecureAIGatewaySimulation extends Simulation {

  // ---------------------------------------------------------------------------
  // Configuration
  // ---------------------------------------------------------------------------

  val baseUrl: String = System.getProperty("baseUrl", "http://localhost:8080")
  val testUsername: String = System.getProperty("testUsername", "perftest_user")
  val testPassword: String = System.getProperty("testPassword", "PerfTest@2024")

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling/SecureAIGateway-PerfTest")
    .shareConnections

  // ---------------------------------------------------------------------------
  // Feeders
  // ---------------------------------------------------------------------------

  val promptFeeder = Array(
    Map("prompt" -> "What is a healthy breakfast option for weight loss?"),
    Map("prompt" -> "How many calories are in a banana?"),
    Map("prompt" -> "What are the benefits of drinking water?"),
    Map("prompt" -> "Suggest a high-protein meal plan for athletes"),
    Map("prompt" -> "What vitamins are found in spinach?"),
    Map("prompt" -> "How does intermittent fasting work?"),
    Map("prompt" -> "What are good sources of omega-3 fatty acids?"),
    Map("prompt" -> "How much protein should I eat per day?"),
    Map("prompt" -> "What are the health benefits of green tea?"),
    Map("prompt" -> "List foods that are high in fiber"),
    Map("prompt" -> "What is the glycemic index of white rice?"),
    Map("prompt" -> "How can I reduce my sugar intake?"),
    Map("prompt" -> "What are the best foods for heart health?"),
    Map("prompt" -> "Explain the Mediterranean diet"),
    Map("prompt" -> "What nutrients does salmon provide?"),
    Map("prompt" -> "How many servings of vegetables should I eat daily?"),
    Map("prompt" -> "What are the side effects of too much caffeine?"),
    Map("prompt" -> "Suggest a vegan meal plan for beginners"),
    Map("prompt" -> "What is the difference between good and bad cholesterol?"),
    Map("prompt" -> "How does vitamin D affect bone health?")
  ).circular

  val userFeeder = (1 to 200).map(i =>
    Map("username" -> s"${testUsername}_$i", "password" -> testPassword)
  ).toArray.circular

  // ---------------------------------------------------------------------------
  // Request Definitions
  // ---------------------------------------------------------------------------

  def login = exec(
    http("Login")
      .post("/api/auth/login")
      .body(StringBody("""{"username":"#{username}","password":"#{password}"}"""))
      .check(status.is(200))
      .check(jsonPath("$.token").saveAs("jwt_token"))
  )

  def askEndpoint = exec(
    http("Ask Endpoint")
      .post("/api/ask")
      .header("Authorization", "Bearer #{jwt_token}")
      .body(StringBody("""{"prompt":"#{prompt}","useReActAgent":false}"""))
      .check(status.in(200, 429))
  )

  def statusEndpoint = exec(
    http("Status Endpoint")
      .get("/api/status")
      .header("Authorization", "Bearer #{jwt_token}")
      .check(status.is(200))
  )

  // ---------------------------------------------------------------------------
  // Scenario 1: Basic Flow
  // ---------------------------------------------------------------------------

  val basicFlowScenario = scenario("Basic Flow")
    .feed(userFeeder)
    .exec(login)
    .pause(500.milliseconds, 1.second)
    .feed(promptFeeder)
    .exec(askEndpoint)
    .pause(300.milliseconds, 800.milliseconds)
    .exec(statusEndpoint)

  // ---------------------------------------------------------------------------
  // Scenario 2: Heavy Load
  // ---------------------------------------------------------------------------

  val heavyLoadScenario = scenario("Heavy Load")
    .feed(userFeeder)
    .exec(login)
    .pause(200.milliseconds, 500.milliseconds)
    .repeat(10) {
      feed(promptFeeder)
        .exec(askEndpoint)
        .pause(100.milliseconds, 300.milliseconds)
    }
    .exec(statusEndpoint)

  // ---------------------------------------------------------------------------
  // Scenario 3: Spike Test
  // ---------------------------------------------------------------------------

  val spikeTestScenario = scenario("Spike Test")
    .feed(userFeeder)
    .exec(login)
    .feed(promptFeeder)
    .exec(askEndpoint)
    .exec(statusEndpoint)

  // ---------------------------------------------------------------------------
  // Scenario 4: Endurance Test
  // ---------------------------------------------------------------------------

  val enduranceTestScenario = scenario("Endurance Test")
    .feed(userFeeder)
    .exec(login)
    .pause(500.milliseconds, 1.second)
    .during(5.minutes) {
      feed(promptFeeder)
        .exec(askEndpoint)
        .pause(1.second, 3.seconds)
        .exec(statusEndpoint)
        .pause(500.milliseconds, 1.second)
    }

  // ---------------------------------------------------------------------------
  // Injection Profiles and Setup
  // ---------------------------------------------------------------------------

  setUp(
    basicFlowScenario.inject(
      atOnceUsers(10)
    ),

    heavyLoadScenario.inject(
      rampUsers(100).during(60.seconds)
    ),

    spikeTestScenario.inject(
      heavisideUsers(200).during(10.seconds)
    ),

    enduranceTestScenario.inject(
      constantUsersPerSec(30.0 / 60.0).during(5.minutes).randomized
    )
  )
    .protocols(httpProtocol)
    .assertions(
      global.responseTime.max.lt(3000),
      global.successfulRequests.percent.gt(95.0),
      forAll.responseTime.percentile(95.0).lt(2000),
      forAll.responseTime.percentile(99.0).lt(3000)
    )
}
