package com.mojang.authlib.yggdrasil.response;

/**
 * Friends API error status. Java 8 compatible.
 */
public enum FriendsErrorStatus {
    TOO_MANY_REQUESTS,
    NOT_FOUND,
    ALREADY_EXISTS,
    CANNOT_ADD_SELF,
    INVALID_REQUEST,
    UNKNOWN
}
