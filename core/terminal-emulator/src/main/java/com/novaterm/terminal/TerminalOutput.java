package com.novaterm.terminal;

import java.nio.charset.StandardCharsets;

/** A client which receives callbacks from events triggered by feeding input to a {@link TerminalEmulator}. */
public abstract class TerminalOutput {

    /** Write a string using the UTF-8 encoding to the terminal client. */
    public final void write(String data) {
        if (data == null) return;
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        write(bytes, 0, bytes.length);
    }

    /** Write bytes to the terminal client. */
    public abstract void write(byte[] data, int offset, int count);

    /** Notify the terminal client that the terminal title has changed. */
    public abstract void titleChanged(String oldTitle, String newTitle);

    /** Notify the terminal client that text should be copied to clipboard. */
    public abstract void onCopyTextToClipboard(String text);

    /** Notify the terminal client that text should be pasted from clipboard. */
    public abstract void onPasteTextFromClipboard();

    /**
     * Get the current clipboard text. Used by OSC 52 query ("?") to respond
     * with base64-encoded clipboard contents. Returns null if unavailable.
     */
    public String getClipboardText() { return null; }

    /** Notify the terminal client that a bell character (ASCII 7, bell, BEL, \a, ^G)) has been received. */
    public abstract void onBell();

    public abstract void onColorsChanged();

    /** OSC 7: Shell reports current working directory. */
    public void onOsc7WorkingDirectory(String path) {}

    /** OSC 9: Desktop notification request (iTerm2/Claude Code/Codex). */
    public void onOsc9Notification(String text) {}

    /** OSC 133: Semantic prompt marker (FinalTerm/Claude Code). */
    public void onOsc133SemanticPrompt(String params) {}

}
