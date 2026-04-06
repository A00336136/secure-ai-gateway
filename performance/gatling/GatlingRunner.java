package secureaigateway;

import io.gatling.app.Gatling;
import io.gatling.core.config.GatlingPropertiesBuilder;

/**
 * Runner class to execute Gatling simulations from Maven.
 *
 * Usage with gatling-maven-plugin:
 *   mvn gatling:test -Dgatling.simulationClass=secureaigateway.SecureAIGatewaySimulation
 *
 * Or run directly:
 *   java -cp "target/test-classes:target/dependency/*" secureaigateway.GatlingRunner
 *
 * System properties:
 *   -DbaseUrl=http://localhost:8080   (target server URL)
 *   -DtestUsername=perftest_user       (test account username)
 *   -DtestPassword=PerfTest@2024      (test account password)
 *
 * Maven plugin configuration (pom.xml):
 * {@code
 * <plugin>
 *     <groupId>io.gatling</groupId>
 *     <artifactId>gatling-maven-plugin</artifactId>
 *     <version>4.9.6</version>
 *     <configuration>
 *         <simulationClass>secureaigateway.SecureAIGatewaySimulation</simulationClass>
 *         <resultsFolder>target/gatling-results</resultsFolder>
 *     </configuration>
 * </plugin>
 * }
 */
public class GatlingRunner {

    public static void main(String[] args) {
        GatlingPropertiesBuilder props = new GatlingPropertiesBuilder()
                .simulationClass("secureaigateway.SecureAIGatewaySimulation")
                .resultsDirectory("target/gatling-results")
                .resourcesDirectory("src/test/resources");

        Gatling.fromMap(props.build());
    }
}
