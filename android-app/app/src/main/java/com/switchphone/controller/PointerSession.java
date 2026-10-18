package com.switchphone.controller;

/** Control-surface capability independent of Bluetooth or Wi-Fi. */
public interface PointerSession {
    boolean connected();boolean mouseAllowed();
    void pointer(int x,int y,int scroll);void mouseHeld(boolean down);void click(boolean right);void clearPointerState();
}
