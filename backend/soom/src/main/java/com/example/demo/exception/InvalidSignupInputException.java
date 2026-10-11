package com.example.demo.exception;

public class InvalidSignupInputException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidSignupInputException(String message) {
        super(message);
    }
}
