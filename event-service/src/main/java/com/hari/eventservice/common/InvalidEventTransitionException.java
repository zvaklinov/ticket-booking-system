package com.hari.eventservice.common;

public class InvalidEventTransitionException extends RuntimeException {
    public InvalidEventTransitionException(String message){
        super(message);
    }
}
