package com.fatelocked.guardian.travel;

import com.fatelocked.FateLockedBundle;
import com.fatelocked.MenuFacts;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.api.gameval.InterfaceID;

/**
 * The travel fixture (bundles/v4-travel.json) bound to "Iron Example", with networks as the
 * tracker sends them: the fairy ring's codes with every decision (CKS locked to Karamja, AIQ
 * allowed, CIP not ready, DKR unknown, BLQ locked with nowhere to land), a glider whose Glider
 * goes to several places and whose Glider-to Gandius goes to one, and a charter crew whose
 * Charter goes to several ports and Charter-to Brimhaven to one.
 */
public final class NetworkFixtures
{
    public static final String PLAYER = "iron example";
    public static final int FAIRY_RING = TravelFixtures.FAIRY_RING;
    public static final int PORT_SARIM_CREW = TravelFixtures.PORT_SARIM_CREW;
    public static final int GLIDER_PILOT = 7517;
    public static final int CHARTER_CREW = 9312;

    private static final Gson GSON = new Gson();

    private NetworkFixtures()
    {
    }

    public static String json()
    {
        JsonObject bundle = GSON.fromJson(TravelFixtures.json().replace("Nubles", "Iron Example"), JsonObject.class);
        JsonObject travel = bundle.getAsJsonObject("rules").getAsJsonObject("travel");
        JsonObject codes = travel.getAsJsonObject("network:fairy-ring").getAsJsonObject("codes");
        codes.add("CKS", option("LOCKED", "Karamja", "53,54"));
        codes.add("AIQ", option("ALLOWED", null, "46,48"));
        codes.add("CIP", option("NOT_READY", "Needs Fairytale II", "39,60"));
        codes.add("DKR", option("UNKNOWN", null, "48,54"));
        codes.add("BLQ", option("LOCKED", "Needs Fairy Rings"));

        JsonObject glider = method("Gnome glider", "npcs", GLIDER_PILOT);
        glider.getAsJsonObject("options").add("Glider", option("LOCKED", "Needs Gnome Gliders", "38,54", "46,46"));
        glider.getAsJsonObject("options").add("Glider-to Gandius", option("LOCKED", "Needs Gnome Gliders", "46,46"));
        travel.add("network:gnome-glider", glider);

        JsonObject charter = method("Charter ship", "npcs", CHARTER_CREW);
        charter.getAsJsonObject("options").add("Charter", option("LOCKED", "Needs Charter Ships", "43,50", "47,49"));
        charter.getAsJsonObject("options").add("Charter-to Brimhaven", option("LOCKED", "Unlock Brimhaven", "43,50"));
        charter.getAsJsonObject("options").add("Charter-to Port Sarim", option("ALLOWED", null, "47,49"));
        travel.add("network:charter-ship", charter);
        return bundle.toString();
    }

    /** The rules bound to Iron Example, with Iron Example playing. */
    public static DecisionService rules()
    {
        return DecisionService.create(RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json())), PLAYER, PLAYER);
    }

    /** A left click on the fairy ring dials' confirm button, read with this code (null: unread or unsure). */
    public static MenuFacts confirm(String code)
    {
        return MenuFacts.builder().kind(MenuFacts.Kind.WIDGET).interfaceGroup(InterfaceID.FAIRYRINGS)
            .componentId(InterfaceID.Fairyrings.CONFIRM).option("Confirm").fairyRingCode(code).build();
    }

    private static JsonObject method(String label, String kind, int id)
    {
        JsonObject method = new JsonObject();
        method.addProperty("label", label);
        JsonObject match = new JsonObject();
        JsonArray ids = new JsonArray();
        ids.add(id);
        match.add(kind, ids);
        method.add("match", match);
        method.add("options", new JsonObject());
        method.addProperty("advisory", true);
        return method;
    }

    private static JsonObject option(String status, String reason, String... to)
    {
        JsonObject option = new JsonObject();
        JsonArray chunks = new JsonArray();
        for (String chunk : to) chunks.add(chunk);
        option.add("to", chunks);
        option.addProperty("status", status);
        if (reason != null) option.addProperty("reason", reason);
        return option;
    }
}
