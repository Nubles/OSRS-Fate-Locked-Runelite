package com.fatelocked.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict validator for the app-owned RuneProof bundle schema v1. */
public final class RuneProofWireValidator
{
    private static final int MAX_SUMMARIES = 20;
    private static final int MAX_BYTES = 32 * 1024;
    private static final int MAX_ID = 256;
    private static final int MAX_SOURCE_VERSION = 160;
    private static final int MAX_DISPLAY_TEXT = 512;
    private static final int MAX_LABEL = 160;
    private static final int MAX_LABELS = 32;
    private static final BigDecimal MAX_SAFE_INTEGER =
        new BigDecimal("9007199254740991");
    private static final Pattern GOAL_ID =
        Pattern.compile("^[a-z][a-z0-9-]*:[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern PROOF_HASH =
        Pattern.compile("^sha256-[0-9a-f]{64}$");
    private static final Set<String> FIELDS = new HashSet<>(Arrays.asList(
        "goalId", "goalLabel", "status", "explanation", "routeLabels",
        "blockerLabels", "unavoidableBlockerLabels", "proofHash",
        "sourceVersion", "runRevision"));
    private static final Set<String> STATUSES = new HashSet<>(Arrays.asList(
        "OBTAINABLE", "OBTAINABLE_RNG", "BLOCKED", "IMPOSSIBLE", "UNKNOWN"));

    private RuneProofWireValidator()
    {
    }

    public static boolean isValidV1(JsonObject rules)
    {
        if (rules == null || !rules.has("runeProof")
            || rules.get("runeProof").isJsonNull())
        {
            return true;
        }
        JsonElement value = rules.get("runeProof");
        if (!value.isJsonArray())
        {
            return false;
        }
        JsonArray summaries = value.getAsJsonArray();
        if (summaries.size() > MAX_SUMMARIES
            || summaries.toString().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
        {
            return false;
        }
        Set<String> goals = new HashSet<>();
        for (JsonElement element : summaries)
        {
            if (!element.isJsonObject()
                || !validSummary(element.getAsJsonObject(), goals))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean validSummary(JsonObject summary, Set<String> goals)
    {
        if (!summary.keySet().equals(FIELDS))
        {
            return false;
        }
        String goalId = text(summary.get("goalId"), MAX_ID);
        String goalLabel = text(summary.get("goalLabel"), MAX_LABEL);
        String status = text(summary.get("status"), 32);
        String explanation = text(summary.get("explanation"), MAX_DISPLAY_TEXT);
        String sourceVersion = text(summary.get("sourceVersion"), MAX_SOURCE_VERSION);
        List<String> routes = labels(summary.get("routeLabels"));
        List<String> blockers = labels(summary.get("blockerLabels"));
        List<String> unavoidable = labels(summary.get("unavoidableBlockerLabels"));
        if (goalId == null || !GOAL_ID.matcher(goalId).matches()
            || !goals.add(goalId) || goalLabel == null || status == null
            || !STATUSES.contains(status) || explanation == null
            || sourceVersion == null || routes == null || blockers == null
            || unavoidable == null || !blockers.containsAll(unavoidable)
            || !safeRevision(summary.get("runRevision")))
        {
            return false;
        }

        JsonElement hashElement = summary.get("proofHash");
        String proofHash = hashElement != null && hashElement.isJsonPrimitive()
            && hashElement.getAsJsonPrimitive().isString()
            ? hashElement.getAsString() : null;
        boolean hashIsNull = hashElement != null && hashElement.isJsonNull();
        boolean positive = "OBTAINABLE".equals(status)
            || "OBTAINABLE_RNG".equals(status);
        if (positive)
        {
            return proofHash != null && PROOF_HASH.matcher(proofHash).matches()
                && !routes.isEmpty();
        }
        if (!hashIsNull || !routes.isEmpty())
        {
            return false;
        }
        if ("BLOCKED".equals(status))
        {
            return !blockers.isEmpty();
        }
        return blockers.isEmpty() && unavoidable.isEmpty();
    }

    private static List<String> labels(JsonElement element)
    {
        if (element == null || !element.isJsonArray())
        {
            return null;
        }
        JsonArray values = element.getAsJsonArray();
        if (values.size() > MAX_LABELS)
        {
            return null;
        }
        List<String> labels = new java.util.ArrayList<>();
        for (JsonElement value : values)
        {
            String label = text(value, MAX_LABEL);
            if (label == null)
            {
                return null;
            }
            if (!labels.contains(label))
            {
                labels.add(label);
            }
        }
        return labels;
    }

    private static String text(JsonElement element, int limit)
    {
        if (element == null || !element.isJsonPrimitive())
        {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isString())
        {
            return null;
        }
        String value = primitive.getAsString();
        if (value.isEmpty() || value.length() > limit || !value.equals(value.trim()))
        {
            return null;
        }
        for (int index = 0; index < value.length(); index++)
        {
            char character = value.charAt(index);
            if (character <= 0x1f || character == 0x7f)
            {
                return null;
            }
        }
        return value;
    }

    private static boolean safeRevision(JsonElement element)
    {
        if (element == null || !element.isJsonPrimitive()
            || !element.getAsJsonPrimitive().isNumber())
        {
            return false;
        }
        try
        {
            BigDecimal value = element.getAsBigDecimal();
            return value.signum() >= 0
                && value.stripTrailingZeros().scale() <= 0
                && value.compareTo(MAX_SAFE_INTEGER) <= 0;
        }
        catch (NumberFormatException error)
        {
            return false;
        }
    }
}
