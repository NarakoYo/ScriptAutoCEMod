package dev.scriptautoce.policy;

import dev.scriptautoce.script.CompiledScript;
import dev.scriptautoce.script.Template;
import java.util.List;

public final class PolicyPreflight {
    private PolicyPreflight() {
    }

    public static List<String> findStaticViolations(CompiledScript script, CommandPolicy policy) {
        return script.instructions().stream()
                .filter(CompiledScript.RunCommand.class::isInstance)
                .map(CompiledScript.RunCommand.class::cast)
                .map(CompiledScript.RunCommand::command)
                .map(Template::staticText)
                .filter(java.util.Objects::nonNull)
                .map(policy::evaluate)
                .filter(decision -> !decision.allowed() && decision.hardBlocked())
                .map(CommandPolicy.PolicyDecision::reason)
                .distinct()
                .toList();
    }
}
