package com.mojang.authlib.yggdrasil.request;

import java.util.Set;
import java.util.UUID;

/**
 * Backport of authlib 7.0.72 JoinInfoUpdate record.
 */
public record JoinInfoUpdate(String value, Set<UUID> invites) {}
