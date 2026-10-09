package dev.aegisac.common.bedrock;
import dev.aegisac.api.player.*;
import java.util.*;
/** A positive official result wins. Provider loss never converts a known Bedrock session into Java. */
public final class IdentityResolver {
    private IdentityResolver() { }
    public static EditionSnapshot resolve(List<IdentityResult> results,boolean authoritativeNegatives,boolean javaOnly,
                                          boolean previouslyBedrock,long now) {
        var positive=results.stream().filter(r->r.outcome()==IdentityResult.Outcome.POSITIVE).toList();
        Set<String> reasons=new HashSet<>(); results.forEach(r->reasons.addAll(r.reasons()));
        if(!positive.isEmpty()) {
            var metadata=positive.stream().filter(p->!p.device().equals("UNKNOWN")||!p.input().equals("UNKNOWN")).findFirst().orElse(positive.getFirst());
            if(results.stream().anyMatch(r->r.outcome()==IdentityResult.Outcome.FAILURE)) reasons.add("SECONDARY_PROVIDER_FAILURE");
            if(javaOnly) reasons.add("JAVA_ONLY_ASSERTION_CONTRADICTED");
            return new EditionSnapshot(BedrockStatus.BEDROCK,positive.stream().map(IdentityResult::provider).sorted().collect(java.util.stream.Collectors.joining("+")),
                    metadata.device(),metadata.input(),metadata.version(),now,reasons);
        }
        boolean failure=results.stream().anyMatch(r->r.outcome()==IdentityResult.Outcome.FAILURE);
        boolean negative=results.stream().anyMatch(r->r.outcome()==IdentityResult.Outcome.NEGATIVE);
        boolean complete=negative&&results.stream().allMatch(r->r.outcome()==IdentityResult.Outcome.NEGATIVE||r.outcome()==IdentityResult.Outcome.DISABLED);
        if(previouslyBedrock) reasons.add("BEDROCK_PROVIDER_LOST");
        else if(!failure&&(javaOnly||authoritativeNegatives&&complete))
            return new EditionSnapshot(BedrockStatus.JAVA,javaOnly?"CONFIGURED_JAVA_ONLY":"AUTHORITATIVE_NEGATIVES","UNKNOWN","UNKNOWN","unknown",now,Set.of("OPERATOR_TOPOLOGY_ASSERTION"));
        reasons.add(failure?"PROVIDER_FAILURE":negative?"NEGATIVE_NOT_AUTHORITATIVE":"PROVIDER_UNAVAILABLE");
        return new EditionSnapshot(BedrockStatus.UNKNOWN,"NONE","UNKNOWN","UNKNOWN","unknown",now,reasons);
    }
}
