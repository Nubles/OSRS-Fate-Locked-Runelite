package com.fatelocked;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AccountBindingTest
{
    @Test
    public void namesCompareAsTheTrackerComparesThem()
    {
        assertTrue(AccountBinding.sameAccount("Iron Example", "iron example"));
        assertTrue(AccountBinding.sameAccount("Iron Example", " Iron Example "));
        assertTrue(AccountBinding.sameAccount("Iron Example", "Iron  Example"));
        assertTrue(AccountBinding.sameAccount("Iron Example", "Iron Example"));
        assertFalse(AccountBinding.sameAccount("Iron Example", "Iron_Example"));
        assertFalse(AccountBinding.sameAccount("Iron Example", "Zezima"));
        assertFalse(AccountBinding.sameAccount("  ", "  "));
        assertFalse(AccountBinding.sameAccount(null, "Zezima"));
        assertFalse(AccountBinding.sameAccount("Iron Example", null));
    }

    @Test
    public void theRulesAccountIsTheBoundAccount() throws Exception
    {
        JsonObject root = fixture();
        root.getAsJsonObject("state").addProperty("linkedAccount", "Somebody Else");
        root.getAsJsonObject("rules").addProperty("account", " Nubles ");

        assertEquals("Nubles", AccountBinding.boundAccount(bundle(root)));
    }

    @Test
    public void aProfileWithoutAnAccountIsUnbound() throws Exception
    {
        JsonObject root = fixture();
        root.getAsJsonObject("rules").addProperty("account", "  ");

        // The rules decide, even when the older field still names someone.
        assertNull(AccountBinding.boundAccount(bundle(root)));
        assertNull(AccountBinding.boundAccount(FateLockedBundle.empty()));
        assertNull(AccountBinding.boundAccount(null));
    }

    @Test
    public void olderBundlesWithoutRulesUseTheLinkedAccount() throws Exception
    {
        FateLockedBundle legacy = FateLockedBundle.loadFromJson(new Gson(),
            resource("bundles/v3-standard.json"));

        assertEquals(legacy.getState() == null ? null : trimmedOrNull(
                legacy.getState().getLinkedAccount()),
            AccountBinding.boundAccount(legacy));
    }

    private static String trimmedOrNull(String name)
    {
        return name == null || name.trim().isEmpty() ? null : name.trim();
    }

    private static FateLockedBundle bundle(JsonObject root)
    {
        return FateLockedBundle.loadFromJson(new Gson(), root.toString());
    }

    private static JsonObject fixture() throws Exception
    {
        return new Gson().fromJson(resource("bundles/v4-rules.json"), JsonObject.class);
    }

    private static String resource(String name) throws Exception
    {
        try (InputStream in = AccountBindingTest.class.getClassLoader().getResourceAsStream(name))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
