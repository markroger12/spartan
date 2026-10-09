package dev.aegisac.common.bedrock;
import dev.aegisac.api.player.BedrockStatus;
import org.junit.jupiter.api.Test;
import java.util.*;
import static dev.aegisac.common.bedrock.IdentityResult.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;
class IdentityResolverTest {
    IdentityResult result(IdentityResult.Outcome outcome) { return IdentityResult.of("API",outcome); }
    @Test void positiveWinsEvenWhenOtherProviderFailsOrJavaOnlyWasAsserted() {
        var value=IdentityResolver.resolve(List.of(result(POSITIVE),result(FAILURE)),true,true,false,12);
        assertEquals(BedrockStatus.BEDROCK,value.status()); assertEquals(12,value.observedNanos());
        assertTrue(value.reasons().containsAll(Set.of("SECONDARY_PROVIDER_FAILURE","JAVA_ONLY_ASSERTION_CONTRADICTED")));
    }
    @Test void negativesAreNotIdentityWithoutExplicitCoverageAssertion() {
        for(var outcome:List.of(NEGATIVE,ABSENT,FAILURE,DISABLED))
            assertEquals(BedrockStatus.UNKNOWN,IdentityResolver.resolve(List.of(result(outcome)),false,false,false,0).status());
    }
    @Test void everyConfiguredProviderMustAnswerNegative() {
        for(var outcome:List.of(ABSENT,FAILURE))
            assertEquals(BedrockStatus.UNKNOWN,IdentityResolver.resolve(List.of(result(NEGATIVE),result(outcome)),true,false,false,0).status());
        for(var outcome:List.of(NEGATIVE,DISABLED))
            assertEquals(BedrockStatus.JAVA,IdentityResolver.resolve(List.of(result(NEGATIVE),result(outcome)),true,false,false,0).status());
        assertEquals(BedrockStatus.UNKNOWN,IdentityResolver.resolve(List.of(result(DISABLED)),true,false,false,0).status());
    }
    @Test void providerLossCannotReclassifyBedrockAsJavaEvenWithOperatorAssertions() {
        for(var outcome:List.of(NEGATIVE,ABSENT,FAILURE,DISABLED)) {
            var value=IdentityResolver.resolve(List.of(result(outcome)),true,true,true,0);
            assertEquals(BedrockStatus.UNKNOWN,value.status()); assertTrue(value.reasons().contains("BEDROCK_PROVIDER_LOST"));
        }
    }
    @Test void javaOnlyAssertionWorksWithoutProvidersButNotThroughApiFailures() {
        assertEquals(BedrockStatus.JAVA,IdentityResolver.resolve(List.of(result(ABSENT)),false,true,false,0).status());
        assertEquals(BedrockStatus.UNKNOWN,IdentityResolver.resolve(List.of(result(FAILURE)),false,true,false,0).status());
    }
}
