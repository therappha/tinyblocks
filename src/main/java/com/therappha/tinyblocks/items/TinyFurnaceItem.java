package com.therappha.tinyblocks.items;

import com.therappha.tinyblocks.subgrid.PieceDefinition;
import com.therappha.tinyblocks.subgrid.TinyFurnaceDefinition;

public class TinyFurnaceItem extends TinyPieceItem {

    public TinyFurnaceItem(Properties properties) {
        super(properties);
    }

    @Override
    public PieceDefinition pieceDefinition() {
        return TinyFurnaceDefinition.INSTANCE;
    }

    @Override
    public boolean showPreview() { return true; }
}
