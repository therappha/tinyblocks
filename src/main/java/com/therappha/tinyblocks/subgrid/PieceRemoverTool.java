package com.therappha.tinyblocks.subgrid;

/**
 * Capability marker: items exposing this remove the targeted subgrid piece on right-click
 * instead of going through vanilla block breaking, which avoids the client-side break
 * prediction that causes the whole SubgridBlock to flicker. Other mods can register this
 * capability for their own tools (e.g. a wrench) via RegisterCapabilitiesEvent.
 */
public interface PieceRemoverTool {
    PieceRemoverTool INSTANCE = new PieceRemoverTool() {};
}
