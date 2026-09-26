package com.fatelocked.rules;

/** Whether the loaded rules apply to the character that is logged in. */
public enum Trust
{
    /** No rules are loaded. */
    NO_RULES,
    /** The rules are bound to an account and nobody is logged in to compare. */
    LOGGED_OUT,
    /** The rules are bound to a different account. */
    WRONG_CHARACTER,
    /** The rules apply: bound to this character, or bound to no one. */
    TRUSTED
}
