package com.therappha.tinyblocks.items;

import com.therappha.tinyblocks.subgrid.PieceDefinition;
import com.therappha.tinyblocks.subgrid.PieceDefinitions;

public class TinyRedstoneDustItem extends TinyPieceItem {

    public TinyRedstoneDustItem(Properties properties) {
        super(properties);
    }

    @Override
    public PieceDefinition pieceDefinition() {
        return PieceDefinitions.TINY_REDSTONE_DUST;
    }
}
