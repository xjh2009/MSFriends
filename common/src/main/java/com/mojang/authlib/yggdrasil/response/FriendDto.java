package com.mojang.authlib.yggdrasil.response;

import java.util.UUID;

/**
 * Backport of authlib 7.0.72 FriendDto record.
 */
public record FriendDto(UUID profileId, String name) {}
