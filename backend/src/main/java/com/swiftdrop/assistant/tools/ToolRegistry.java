package com.swiftdrop.assistant.tools;

import com.swiftdrop.assistant.dto.response.AssistantCard;
import com.swiftdrop.assistant.llm.LlmRequest;
import com.swiftdrop.assistant.llm.ToolCall;
import com.swiftdrop.assistant.llm.ToolDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The allowlist of tools the model may call, and the only way they are run. Unknown tools,
 * arguments that aren't a JSON object, unknown argument names and invalid values all become error
 * results for the model; nothing the model writes reaches a tool unchecked.
 *
 * <p>Logs hold only the tool name, how long it took and the outcome, never the arguments.
 */
@Slf4j
@Component
public class ToolRegistry {

    /** Rounds of tool calls in one question; after that the customer is asked to rephrase. */
    public static final int MAX_ROUNDS = 3;
    /** Calls the model may ask for in one round; extra calls get an error result. */
    static final int MAX_CALLS_PER_ROUND = 4;

    static final String UNKNOWN_TOOL = "UNKNOWN_TOOL";
    static final String INVALID_INPUT = "INVALID_INPUT";
    static final String TOO_MANY_CALLS = "TOO_MANY_CALLS";

    private final Map<String, AssistantTool> tools;
    private final JsonMapper json;

    @Autowired
    public ToolRegistry(ObjectProvider<AssistantTool> tools, JsonMapper json) {
        this(tools.orderedStream().toList(), json);
    }

    ToolRegistry(List<AssistantTool> tools, JsonMapper json) {
        this.tools = tools.stream().collect(Collectors.toMap(AssistantTool::name, Function.identity(),
                (first, second) -> {
                    throw new IllegalStateException("Two assistant tools are named " + first.name());
                }, LinkedHashMap::new));
        this.json = json;
    }

    /** Every tool, offered to the model. */
    public List<ToolDefinition> definitions() {
        return tools.values().stream().map(AssistantTool::definition).toList();
    }

    /** Runs one tool directly, without the model (the fee form). Arguments are checked the same way. */
    public ToolOutput run(String toolName, JsonNode arguments, ToolContext context) {
        return execute(toolName, arguments, context);
    }

    /** Starts answering one question for {@code context}'s customer. */
    public Turn startTurn(ToolContext context) {
        return new Turn(context);
    }

    /** The tool calls made while answering one question: counts rounds and collects cards. */
    public final class Turn {

        private final ToolContext context;
        private final Set<AssistantCard> cards = new LinkedHashSet<>();
        private int rounds;

        private Turn(ToolContext context) {
            this.context = context;
        }

        public boolean canRunAnotherRound() {
            return rounds < MAX_ROUNDS;
        }

        /**
         * Runs one round of calls and returns their results, in the same order, to send back.
         *
         * @throws IllegalStateException if {@link #MAX_ROUNDS} rounds were already run
         */
        public List<LlmRequest.ToolResult> runRound(List<ToolCall> calls) {
            if (!canRunAnotherRound()) {
                throw new IllegalStateException("No tool rounds left");
            }
            rounds++;
            List<LlmRequest.ToolResult> results = new ArrayList<>(calls.size());
            for (int i = 0; i < calls.size(); i++) {
                ToolCall call = calls.get(i);
                ToolOutput output = i < MAX_CALLS_PER_ROUND
                        ? execute(call.name(), parse(call.arguments()), context)
                        : error(TOO_MANY_CALLS, "Too many tools were asked for at once.");
                if (output.card() != null) {
                    cards.add(output.card());
                }
                results.add(new LlmRequest.ToolResult(call.id(), call.name(), json.writeValueAsString(output.result())));
            }
            return results;
        }

        /** Cards from this question's tool results, without repeats, oldest first. */
        public List<AssistantCard> cards() {
            return List.copyOf(cards);
        }
    }

    private ToolOutput execute(String toolName, JsonNode arguments, ToolContext context) {
        AssistantTool tool = toolName == null ? null : tools.get(toolName);
        if (tool == null) {
            log.info("Assistant tool rejected: unknown tool");
            return error(UNKNOWN_TOOL, "That tool doesn't exist.");
        }
        long started = System.nanoTime();
        String outcome = "ok";
        try {
            checkArgumentNames(tool, arguments);
            return tool.run(new ToolArguments(arguments), context);
        } catch (InvalidToolArgumentException ex) {
            outcome = "invalid " + ex.field();
            ObjectNode result = error(INVALID_INPUT, "Some details are missing or not valid: " + ex.field() + ".")
                    .result();
            return ToolOutput.of(result.put("field", ex.field()));
        } finally {
            log.info("Assistant tool {} took {} ms: {}", tool.name(), (System.nanoTime() - started) / 1_000_000,
                    outcome);
        }
    }

    /** Only the names in the tool's schema are allowed, like {@code additionalProperties: false}. */
    private static void checkArgumentNames(AssistantTool tool, JsonNode arguments) {
        if (arguments == null || !arguments.isObject()) {
            throw new InvalidToolArgumentException("arguments", "must be a JSON object");
        }
        Set<String> known = tool.definition().parameters().get("properties") instanceof Map<?, ?> properties
                ? properties.keySet().stream().map(String::valueOf).collect(Collectors.toSet())
                : Set.of();
        for (String name : arguments.propertyNames()) {
            if (!known.contains(name)) {
                // The name is the model's text: don't echo it into results or logs.
                throw new InvalidToolArgumentException("arguments", "include an unknown name");
            }
        }
    }

    /** The model's argument string as JSON; {@code null} (rejected later) when it isn't JSON. */
    private JsonNode parse(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(arguments);
        } catch (JacksonException ex) {
            return null;
        }
    }

    private ToolOutput error(String code, String message) {
        return ToolOutput.of(json.createObjectNode().put("error", code).put("message", message));
    }
}
