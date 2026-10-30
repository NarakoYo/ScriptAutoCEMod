package dev.scriptautoce.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class CommandPolicyTest {
    @Test
    void blocksDangerousCommandRoots() {
        CommandPolicy policy = CommandPolicy.defaults();

        assertFalse(policy.evaluate("stop").allowed());
        assertFalse(policy.evaluate("/minecraft:op Alex").allowed());
        assertTrue(policy.evaluate("say hello").allowed());
    }

    @Test
    void checksNestedExecuteRunCommands() {
        CommandPolicy policy = CommandPolicy.defaults();

        assertFalse(policy.evaluate("execute as @a run stop").allowed());
        assertFalse(policy.evaluate("execute as @a at @s run execute run deop Alex").allowed());
        assertTrue(policy.evaluate("execute as @a run say hello").allowed());
    }

    @Test
    void ignoreQuotedRunWords() {
        CommandPolicy policy = CommandPolicy.defaults();

        assertTrue(policy.evaluate("execute as @a run say \"run stop\"").allowed());
    }

    @Test
    void allowlistCanExplicitlyRelaxDefaultBlock() {
        CommandPolicy policy = new CommandPolicy(CommandPolicy.DEFAULT_BLOCKED, Set.of("stop", "say"));

        assertTrue(policy.evaluate("stop").allowed());
        assertTrue(policy.evaluate("say hello").allowed());
        assertFalse(policy.evaluate("op Alex").allowed());
    }

    @Test
    void allowsCarpetFakePlayerSpawnCommand() {
        CommandPolicy policy = CommandPolicy.defaults();

        assertTrue(policy.evaluate("player sace_bot_0 spawn at 10 64 -2").allowed());
    }
}
