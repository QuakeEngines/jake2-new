/*
 * Copyright (C) 1997-2001 Id Software, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *  
 */

// Created on 27.12.2003 by RST.

package jake2.game;

import jake2.game.adapters.EntThinkAdapter;
import jake2.game.adapters.EntTouchAdapter;
import jake2.game.adapters.EntUseAdapter;
import jake2.qcommon.*;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

class GameTrigger {

    private static void InitTrigger(SubgameEntity self, GameExportsImpl gameExports) {
        if (!Math3D.VectorEquals(self.s.angles, Globals.vec3_origin))
            GameBase.G_SetMovedir(self.s.angles, self.movedir);

        self.solid = Defines.SOLID_TRIGGER;
        self.movetype = GameDefines.MOVETYPE_NONE;
        gameExports.gameImports.setmodel(self, self.model);
        self.svflags = Defines.SVF_NOCLIENT;
    }

    // the trigger was just activated
    // ent.activator should be set to the activator so it can be held through a
    // delay so wait for the delay time before firing
    private static void multi_trigger(SubgameEntity ent, GameExportsImpl gameExports) {
        if (ent.nextthink != 0)
            return; // already been triggered

        GameUtil.G_UseTargets(ent, ent.activator, gameExports);

        if (ent.wait > 0) {
            ent.think = multi_wait;
            ent.nextthink = gameExports.level.time + ent.wait;
        } else { // we can't just remove (self) here, because this is a touch
                 // function
            // called while looping through area links...
            ent.touch = null;
            ent.nextthink = gameExports.level.time + Defines.FRAMETIME;
            ent.think = GameUtil.G_FreeEdictA;
        }
    }

    static void SP_trigger_multiple(SubgameEntity ent, GameExportsImpl gameExports) {
        if (ent.sounds == 1)
            ent.noise_index = gameExports.gameImports.soundindex("misc/secret.wav");
        else if (ent.sounds == 2)
            ent.noise_index = gameExports.gameImports.soundindex("misc/talk.wav");
        else if (ent.sounds == 3)
            ent.noise_index = gameExports.gameImports.soundindex("misc/trigger1.wav");

        if (ent.wait == 0)
            ent.wait = 0.2f;

        ent.touch = Touch_Multi;
        ent.movetype = GameDefines.MOVETYPE_NONE;
        ent.svflags |= Defines.SVF_NOCLIENT;

        if ((ent.spawnflags & 4) != 0) {
            ent.solid = Defines.SOLID_NOT;
            ent.use = trigger_enable;
        } else {
            ent.solid = Defines.SOLID_TRIGGER;
            ent.use = Use_Multi;
        }

        if (!Math3D.VectorEquals(ent.s.angles, Globals.vec3_origin))
            GameBase.G_SetMovedir(ent.s.angles, ent.movedir);

        gameExports.gameImports.setmodel(ent, ent.model);
        gameExports.gameImports.linkentity(ent);
    }

    /**
     * QUAKED trigger_once (.5 .5 .5) ? x x TRIGGERED Triggers once, then
     * removes itself. You must set the key "target" to the name of another
     * object in the level that has a matching "targetname".
     * 
     * If TRIGGERED, this trigger must be triggered before it is live.
     * 
     * sounds 1) secret 2) beep beep 3) large switch 4)
     * 
     * "message" string to be displayed when triggered
     */

    static void SP_trigger_once(SubgameEntity ent, GameExportsImpl gameExports) {
        // make old maps work because I messed up on flag assignments here
        // triggered was on bit 1 when it should have been on bit 4
        if ((ent.spawnflags & 1) != 0) {
            float[] v = { 0, 0, 0 };

            Math3D.VectorMA(ent.mins, 0.5f, ent.size, v);
            ent.spawnflags &= ~1;
            ent.spawnflags |= 4;
            gameExports.gameImports.dprintf("fixed TRIGGERED flag on " + ent.classname
                    + " at " + Lib.vtos(v) + "\n");
        }

        ent.wait = -1;
        SP_trigger_multiple(ent, gameExports);
    }

    static void SP_trigger_relay(SubgameEntity self) {
        self.use = trigger_relay_use;
    }

    static void SP_trigger_key(SubgameEntity self, GameExportsImpl gameExports) {
        if (self.st.item == null) {
            gameExports.gameImports.dprintf("no key item for trigger_key at "
                    + Lib.vtos(self.s.origin) + "\n");
            return;
        }
        self.item = GameItems.FindItemByClassname(self.st.item, gameExports);

        if (null == self.item) {
            gameExports.gameImports.dprintf("item " + self.st.item
                    + " not found for trigger_key at "
                    + Lib.vtos(self.s.origin) + "\n");
            return;
        }

        if (self.target == null) {
            gameExports.gameImports.dprintf(self.classname + " at "
                    + Lib.vtos(self.s.origin) + " has no target\n");
            return;
        }

        gameExports.gameImports.soundindex("misc/keytry.wav");
        gameExports.gameImports.soundindex("misc/keyuse.wav");

        self.use = trigger_key_use;
    }

    static void SP_trigger_counter(SubgameEntity self) {
        self.wait = -1;
        if (0 == self.count)
            self.count = 2;

        self.use = trigger_counter_use;
    }

    /*
     * ==============================================================================
     * 
     * trigger_always
     * 
     * ==============================================================================
     */

    /*
     * QUAKED trigger_always (.5 .5 .5) (-8 -8 -8) (8 8 8) This trigger will
     * always fire. It is activated by the world.
     */
    static void SP_trigger_always(SubgameEntity ent, GameExportsImpl gameExports) {
        // we must have some delay to make sure our use targets are present
        if (ent.delay < 0.2f)
            ent.delay = 0.2f;
        GameUtil.G_UseTargets(ent, ent, gameExports);
    }

    /*
     * QUAKED trigger_push (.5 .5 .5) ? PUSH_ONCE Pushes the player "speed"
     * defaults to 1000
     */
    static void SP_trigger_push(SubgameEntity self, GameExportsImpl gameExports) {
        InitTrigger(self, gameExports);
        gameExports.windsound_index = gameExports.gameImports.soundindex("misc/windfly.wav");
        self.touch = trigger_push_touch;
        if (0 == self.speed)
            self.speed = 1000;
        gameExports.gameImports.linkentity(self);
    }

    static void SP_trigger_hurt(SubgameEntity self, GameExportsImpl gameExports) {
        InitTrigger(self, gameExports);

        self.noise_index = gameExports.gameImports.soundindex("world/electro.wav");
        self.touch = hurt_touch;

        if (0 == self.dmg)
            self.dmg = 5;

        if ((self.spawnflags & 1) != 0)
            self.solid = Defines.SOLID_NOT;
        else
            self.solid = Defines.SOLID_TRIGGER;

        if ((self.spawnflags & 2) != 0)
            self.use = hurt_use;

        gameExports.gameImports.linkentity(self);
    }

    static void SP_trigger_gravity(SubgameEntity self, GameExportsImpl gameExports) {
        if (self.st.gravity == null) {
            gameExports.gameImports.dprintf("trigger_gravity without gravity set at "
                    + Lib.vtos(self.s.origin) + "\n");
            gameExports.freeEntity(self);
            return;
        }

        InitTrigger(self, gameExports);
        self.gravity = Lib.atoi(self.st.gravity);
        self.touch = trigger_gravity_touch;
    }

    static void SP_trigger_monsterjump(SubgameEntity self, GameExportsImpl gameExports) {
        if (0 == self.speed)
            self.speed = 200;
        if (0 == self.st.height)
            self.st.height = 200;
        if (self.s.angles[Defines.YAW] == 0)
            self.s.angles[Defines.YAW] = 360;
        InitTrigger(self, gameExports);
        self.touch = trigger_monsterjump_touch;
        self.movedir[2] = self.st.height;
    }

    // the wait time has passed, so set back up for another activation
    private static EntThinkAdapter multi_wait = new EntThinkAdapter() {
    	public String getID(){ return "multi_wait"; }
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {

            ent.nextthink = 0;
            return true;
        }
    };

    private static EntUseAdapter Use_Multi = new EntUseAdapter() {
    	public String getID(){ return "Use_Multi"; }
        public void use(SubgameEntity ent, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            ent.activator = activator;
            multi_trigger(ent, gameExports);
        }
    };

    private static EntTouchAdapter Touch_Multi = new EntTouchAdapter() {
    	public String getID(){ return "Touch_Multi"; }
        public void touch(SubgameEntity self, SubgameEntity other, cplane_t plane,
                          csurface_t surf, GameExportsImpl gameExports) {
            if (other.getClient() != null) {
                if ((self.spawnflags & 2) != 0)
                    return;
            } else if ((other.svflags & Defines.SVF_MONSTER) != 0) {
                if (0 == (self.spawnflags & 1))
                    return;
            } else
                return;

            if (!Math3D.VectorEquals(self.movedir, Globals.vec3_origin)) {
                float[] forward = { 0, 0, 0 };

                Math3D.AngleVectors(other.s.angles, forward, null, null);
                if (Math3D.DotProduct(forward, self.movedir) < 0)
                    return;
            }

            self.activator = other;
            multi_trigger(self, gameExports);
        }
    };

    /**
     * QUAKED trigger_multiple (.5 .5 .5) ? MONSTER NOT_PLAYER TRIGGERED
     * Variable sized repeatable trigger. Must be targeted at one or more
     * entities. If "delay" is set, the trigger waits some time after activating
     * before firing. "wait" : Seconds between triggerings. (.2 default) sounds
     * 1) secret 2) beep beep 3) large switch 4) set "message" to text string
     */
    private static EntUseAdapter trigger_enable = new EntUseAdapter() {
    	public String getID(){ return "trigger_enable"; }
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            self.solid = Defines.SOLID_TRIGGER;
            self.use = Use_Multi;
            gameExports.gameImports.linkentity(self);
        }
    };

    /**
     * QUAKED trigger_relay (.5 .5 .5) (-8 -8 -8) (8 8 8) This fixed size
     * trigger cannot be touched, it can only be fired by other events.
     */
    private static EntUseAdapter trigger_relay_use = new EntUseAdapter() {
    	public String getID(){ return "trigger_relay_use"; }
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            GameUtil.G_UseTargets(self, activator, gameExports);
        }
    };

    /*
     * ==============================================================================
     * 
     * trigger_key
     * 
     * ==============================================================================
     */

    /**
     * QUAKED trigger_key (.5 .5 .5) (-8 -8 -8) (8 8 8) A relay trigger that
     * only fires it's targets if player has the proper key. Use "item" to
     * specify the required key, for example "key_data_cd"
     */

    private static EntUseAdapter trigger_key_use = new EntUseAdapter() {
    	public String getID(){ return "trigger_key_use"; }
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            int index;

            if (self.item == null)
                return;
            gclient_t activatorClient = activator.getClient();
            if (activatorClient == null)
                return;

            index = self.item.index;
            if (activatorClient.pers.inventory[index] == 0) {
                if (gameExports.level.time < self.touch_debounce_time)
                    return;
                self.touch_debounce_time = gameExports.level.time + 5.0f;
                gameExports.gameImports.centerprintf(activator, "You need the "
                        + self.item.pickup_name);
                gameExports.gameImports.sound(activator, Defines.CHAN_AUTO,
                        gameExports.gameImports.soundindex("misc/keytry.wav"), 1,
                                Defines.ATTN_NORM, 0);
                return;
            }

            gameExports.gameImports.sound(activator, Defines.CHAN_AUTO, gameExports.gameImports
                    .soundindex("misc/keyuse.wav"), 1, Defines.ATTN_NORM, 0);
            if (gameExports.gameCvars.coop.value != 0) {
                int player;
                SubgameEntity ent;

                if ("key_power_cube".equals(self.item.classname)) {
                    int cube;

                    for (cube = 0; cube < 8; cube++)
                        if ((activatorClient.pers.power_cubes & (1 << cube)) != 0)
                            break;
                    for (player = 1; player <= gameExports.game.maxclients; player++) {
                        ent = gameExports.g_edicts[player];
                        if (!ent.inuse)
                            continue;
                        gclient_t client = ent.getClient();
                        if (client == null)
                            continue;
                        if ((client.pers.power_cubes & (1 << cube)) != 0) {
                            client.pers.inventory[index]--;
                            client.pers.power_cubes &= ~(1 << cube);
                        }
                    }
                } else {
                    for (player = 1; player <= gameExports.game.maxclients; player++) {
                        ent = gameExports.g_edicts[player];
                        if (!ent.inuse)
                            continue;
                        gclient_t client = ent.getClient();
                        if (client == null)
                            continue;
                        client.pers.inventory[index] = 0;
                    }
                }
            } else {
                activatorClient.pers.inventory[index]--;
            }

            GameUtil.G_UseTargets(self, activator, gameExports);

            self.use = null;
        }
    };

    /**
     * QUAKED trigger_counter (.5 .5 .5) ? nomessage Acts as an intermediary for
     * an action that takes multiple inputs.
     * 
     * If nomessage is not set, t will print "1 more.. " etc when triggered and
     * "sequence complete" when finished.
     * 
     * After the counter has been triggered "count" times (default 2), it will
     * fire all of it's targets and remove itself.
     */
    private static EntUseAdapter trigger_counter_use = new EntUseAdapter() {
    	public String getID(){ return "trigger_counter_use"; }

        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            if (self.count == 0)
                return;

            self.count--;

            if (self.count != 0) {
                if (0 == (self.spawnflags & 1)) {
                    gameExports.gameImports.centerprintf(activator, self.count
                            + " more to go...");
                    gameExports.gameImports.sound(activator, Defines.CHAN_AUTO, gameExports.gameImports
                            .soundindex("misc/talk1.wav"), 1,
                            Defines.ATTN_NORM, 0);
                }
                return;
            }

            if (0 == (self.spawnflags & 1)) {
                gameExports.gameImports.centerprintf(activator, "Sequence completed!");
                gameExports.gameImports.sound(activator, Defines.CHAN_AUTO, gameExports.gameImports
                        .soundindex("misc/talk1.wav"), 1, Defines.ATTN_NORM, 0);
            }
            self.activator = activator;
            multi_trigger(self, gameExports);
        }
    };

    /*
     * ==============================================================================
     * 
     * trigger_push
     * 
     * ==============================================================================
     */

    private static final int PUSH_ONCE = 1;

    private static EntTouchAdapter trigger_push_touch = new EntTouchAdapter() {
    	public String getID(){ return "trigger_push_touch"; }
        public void touch(SubgameEntity self, SubgameEntity other, cplane_t plane,
                          csurface_t surf, GameExportsImpl gameExports) {
            if ("grenade".equals(other.classname)) {
                Math3D.VectorScale(self.movedir, self.speed * 10,
                        other.velocity);
            } else if (other.health > 0) {
                Math3D.VectorScale(self.movedir, self.speed * 10,
                        other.velocity);

                gclient_t otherClient = other.getClient();
                if (otherClient != null) {
                    // don't take falling damage immediately from this
                    Math3D.VectorCopy(other.velocity, otherClient.oldvelocity);
                    if (other.fly_sound_debounce_time < gameExports.level.time) {
                        other.fly_sound_debounce_time = gameExports.level.time + 1.5f;
                        gameExports.gameImports.sound(other, Defines.CHAN_AUTO, gameExports.windsound_index,
                                1, Defines.ATTN_NORM, 0);
                    }
                }
            }
            if ((self.spawnflags & PUSH_ONCE) != 0)
                gameExports.freeEntity(self);
        }
    };


    /**
     * QUAKED trigger_hurt (.5 .5 .5) ? START_OFF TOGGLE SILENT NO_PROTECTION
     * SLOW Any entity that touches this will be hurt.
     * 
     * It does dmg points of damage each server frame
     * 
     * SILENT supresses playing the sound SLOW changes the damage rate to once
     * per second NO_PROTECTION *nothing* stops the damage
     * 
     * "dmg" default 5 (whole numbers only)
     *  
     */
    private static EntUseAdapter hurt_use = new EntUseAdapter() {
    	public String getID(){ return "hurt_use"; }

        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            if (self.solid == Defines.SOLID_NOT)
                self.solid = Defines.SOLID_TRIGGER;
            else
                self.solid = Defines.SOLID_NOT;
            gameExports.gameImports.linkentity(self);

            if (0 == (self.spawnflags & 2))
                self.use = null;
        }
    };

    private static EntTouchAdapter hurt_touch = new EntTouchAdapter() {
    	public String getID(){ return "hurt_touch"; }
        public void touch(SubgameEntity self, SubgameEntity other, cplane_t plane,
                          csurface_t surf, GameExportsImpl gameExports) {
            int dflags;

            if (other.takedamage == 0)
                return;

            if (self.timestamp > gameExports.level.time)
                return;

            if ((self.spawnflags & 16) != 0)
                self.timestamp = gameExports.level.time + 1;
            else
                self.timestamp = gameExports.level.time + Defines.FRAMETIME;

            if (0 == (self.spawnflags & 4)) {
                if ((gameExports.level.framenum % 10) == 0)
                    gameExports.gameImports.sound(other, Defines.CHAN_AUTO,
                            self.noise_index, 1, Defines.ATTN_NORM, 0);
            }

            if ((self.spawnflags & 8) != 0)
                dflags = Defines.DAMAGE_NO_PROTECTION;
            else
                dflags = 0;
            GameCombat.T_Damage(other, self, self, Globals.vec3_origin,
                    other.s.origin, Globals.vec3_origin, self.dmg, self.dmg,
                    dflags, GameDefines.MOD_TRIGGER_HURT, gameExports);
        }
    };

    /*
     * ==============================================================================
     * 
     * trigger_gravity
     * 
     * ==============================================================================
     */

    /**
     * QUAKED trigger_gravity (.5 .5 .5) ? Changes the touching entites gravity
     * to the value of "gravity". 1.0 is standard gravity for the level.
     */

    private static EntTouchAdapter trigger_gravity_touch = new EntTouchAdapter() {
    	public String getID(){ return "trigger_gravity_touch"; }

        public void touch(SubgameEntity self, SubgameEntity other, cplane_t plane,
                          csurface_t surf, GameExportsImpl gameExports) {
            other.gravity = self.gravity;
        }
    };

    /*
     * ==============================================================================
     * 
     * trigger_monsterjump
     * 
     * ==============================================================================
     */

    /**
     * QUAKED trigger_monsterjump (.5 .5 .5) ? Walking monsters that touch this
     * will jump in the direction of the trigger's angle "speed" default to 200,
     * the speed thrown forward "height" default to 200, the speed thrown
     * upwards
     */

    private static EntTouchAdapter trigger_monsterjump_touch = new EntTouchAdapter() {
    	public String getID(){ return "trigger_monsterjump_touch"; }
        public void touch(SubgameEntity self, SubgameEntity other, cplane_t plane,
                          csurface_t surf, GameExportsImpl gameExports) {
            if ((other.flags & (GameDefines.FL_FLY | GameDefines.FL_SWIM)) != 0)
                return;
            if ((other.svflags & Defines.SVF_DEADMONSTER) != 0)
                return;
            if (0 == (other.svflags & Defines.SVF_MONSTER))
                return;

            // set XY even if not on ground, so the jump will clear lips
            other.velocity[0] = self.movedir[0] * self.speed;
            other.velocity[1] = self.movedir[1] * self.speed;

            if (other.groundentity != null)
                return;

            other.groundentity = null;
            other.velocity[2] = self.movedir[2];
        }
    };
}
