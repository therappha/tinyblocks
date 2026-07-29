package com.therappha.tinyblocks.items;

import com.therappha.tinyblocks.subgrid.PieceDefinition;
import com.therappha.tinyblocks.subgrid.PieceDefinitions;

public class TinyRedstoneBlockItem extends TinyPieceItem {

    public TinyRedstoneBlockItem(Properties properties) {
        super(properties);
    }

    @Override
    public PieceDefinition pieceDefinition() {
        return PieceDefinitions.TINY_REDSTONE_BLOCK;
    }
}
