package com.therappha.tinyblocks.items;

import com.therappha.tinyblocks.subgrid.PieceDefinition;
import com.therappha.tinyblocks.subgrid.PieceDefinitions;

public class TinyPistonItem extends TinyPieceItem {

    public TinyPistonItem(Properties properties) {
        super(properties);
    }

    @Override
    public PieceDefinition pieceDefinition() {
        return PieceDefinitions.TINY_PISTON;
    }
}
