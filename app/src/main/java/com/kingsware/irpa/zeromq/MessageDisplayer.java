package com.kingsware.irpa.zeromq;

public interface MessageDisplayer {
    void displayMessage(String text, int duration);

    void displayMessage(String text);
}
