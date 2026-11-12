package com.swiftdrop.assistant.llm;

import com.swiftdrop.assistant.llm.LlmRequest.Message;
import com.swiftdrop.assistant.llm.LlmRequest.Text;
import com.swiftdrop.assistant.llm.LlmRequest.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The offline assistant ("Basic mode"): no key, no network and no cost. It spots a few simple
 * questions in English, asks for the matching tool, and then shows each tool's server-written
 * {@code message}. Used when no language model is configured and as the fallback when one fails.
 */
@Component
@RequiredArgsConstructor
public class RuleBasedLlmClient implements LlmClient {

    static final String TRACK_PARCEL = "track_parcel";
    static final String MY_RECENT_PARCELS = "list_my_recent_parcels";
    static final String SERVICE_INFO = "get_service_info";

    static final String HELP = "I'm in basic mode. I can track a parcel (send me its tracking number, "
            + "like SD7KQ2MX9TRB4H), show your recent parcels, give fee estimates with the fee form, "
            + "and share our contact details and what you can send.";
    static final String FEE_FORM = "To get a price, use the fee form: enter the pickup and drop-off district "
            + "and city and the parcel's weight.";
    static final String HOW_TO_TRACK = "Send me the tracking number (it starts with SD), or open the Track page "
            + "and enter it there to see live updates.";
    static final String NOTHING_FOUND = "Sorry, I couldn't get that information right now.";

    private static final Pattern TRACKING_NUMBER = Pattern.compile("(?i)\\bSD[2-9A-HJKMNP-Z]{12}\\b");
    private static final List<String> FEE_WORDS = List.of("fee", "price", "cost", "how much", "charge");
    private static final List<String> MY_PARCEL_WORDS = List.of("my parcel", "my deliver", "my order",
            "my package", "my shipment");
    private static final List<String> SERVICE_WORDS = List.of("contact", "phone", "email", "call you", "weight",
            "size", "what can i send", "parcel type", "allowed", "address");

    private final JsonMapper json;

    @Override
    public LlmResponse complete(LlmRequest request) {
        List<ToolResult> results = toolResultsSinceLastQuestion(request.messages());
        if (!results.isEmpty()) {
            return text(summarise(results));
        }
        String question = lastQuestion(request.messages()).toLowerCase(Locale.ROOT);

        Matcher number = TRACKING_NUMBER.matcher(question);
        if (number.find() && offers(request, TRACK_PARCEL)) {
            String argument = json.createObjectNode()
                    .put("trackingNumber", number.group().toUpperCase(Locale.ROOT))
                    .toString();
            return callTool(TRACK_PARCEL, argument);
        }
        if (containsAny(question, FEE_WORDS)) {
            return text(FEE_FORM);
        }
        if (containsAny(question, MY_PARCEL_WORDS) && offers(request, MY_RECENT_PARCELS)) {
            return callTool(MY_RECENT_PARCELS, "{}");
        }
        if (question.contains("track")) {
            return text(HOW_TO_TRACK);
        }
        if (containsAny(question, SERVICE_WORDS) && offers(request, SERVICE_INFO)) {
            return callTool(SERVICE_INFO, "{}");
        }
        return text(HELP);
    }

    /** The tool results that came after the customer's latest message, oldest first. */
    private static List<ToolResult> toolResultsSinceLastQuestion(List<Message> messages) {
        List<ToolResult> results = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message instanceof Text text && text.fromUser()) {
                break;
            }
            if (message instanceof ToolResult result) {
                results.addFirst(result);
            }
        }
        return results;
    }

    private static String lastQuestion(List<Message> messages) {
        for (Message message : messages.reversed()) {
            if (message instanceof Text text && text.fromUser()) {
                return text.text();
            }
        }
        return "";
    }

    /** Each tool's {@code message}, one per line; tool results are JSON written by our own tools. */
    private String summarise(List<ToolResult> results) {
        List<String> lines = new ArrayList<>();
        for (ToolResult result : results) {
            try {
                JsonNode message = json.readTree(result.content()).path("message");
                if (message.isString() && !message.asString().isBlank()) {
                    lines.add(message.asString());
                }
            } catch (JacksonException ex) {
                // Not JSON: skip it rather than show raw tool output.
            }
        }
        return lines.isEmpty() ? NOTHING_FOUND : String.join("\n", lines);
    }

    private static boolean offers(LlmRequest request, String toolName) {
        return request.tools().stream().anyMatch(tool -> tool.name().equals(toolName));
    }

    private static boolean containsAny(String text, List<String> words) {
        return words.stream().anyMatch(text::contains);
    }

    private static LlmResponse callTool(String name, String arguments) {
        return new LlmResponse("", List.of(new ToolCall("rules-" + name, name, arguments)), 0,
                LlmResponse.Source.RULES);
    }

    private static LlmResponse text(String text) {
        return new LlmResponse(text, List.of(), 0, LlmResponse.Source.RULES);
    }
}
