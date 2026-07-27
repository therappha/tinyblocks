package com.therappha.tinyblocks.items;

import com.therappha.tinyblocks.subgrid.PieceDefinition;
import com.therappha.tinyblocks.subgrid.PieceDefinitions;

public class TinyStoneBlockItem extends TinyPieceItem {

    public TinyStoneBlockItem(Properties properties) {
        super(properties);
    }

    @Override
    public PieceDefinition pieceDefinition() {
        return PieceDefinitions.STONE_BLOCK;
    }
}
