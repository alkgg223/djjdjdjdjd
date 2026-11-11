package com.rpbusiness.block;

public class FreezerBlock extends StorageBlock {
    public FreezerBlock(Properties p) { super(p, 27, FREEZER); }
    @Override public boolean animated() { return true; }
}
