package com.gunsmod.entity;

import net.minecraft.world.entity.Entity;

/** A vehicle soldiers get into on their own: the jeep, the tank and the helicopter. */
public interface BotRideable {
    BotTeam getBotTeam();

    /** the seat a soldier would take now, -1 when there is none for soldiers */
    int seatForBot(BotEntity bot);

    boolean seatFree(int seat);

    void board(Entity e, int seat);

    /** soldiers only get in while it is in good shape */
    boolean fitForBots();
}
