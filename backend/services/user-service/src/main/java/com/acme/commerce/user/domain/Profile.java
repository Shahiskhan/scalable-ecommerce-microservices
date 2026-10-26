package com.acme.commerce.user.domain;

/** Credential data and role assignment remain exclusively in Firebase. */
public record Profile(String userId, String displayName, long version) { }
