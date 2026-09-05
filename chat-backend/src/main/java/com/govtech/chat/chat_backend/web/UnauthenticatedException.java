package com.govtech.chat.chat_backend.web;

public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("Not logged in");
    }
}
